package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.DEFAULT_AMOUNT
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_OTHER_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.ART
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CMT
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementTable
import io.provenance.digitalcurrency.consortium.domain.MT
import io.provenance.digitalcurrency.consortium.domain.MTT
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MigrationRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.frameworks.toOutput
import io.provenance.digitalcurrency.consortium.getBurnEvent
import io.provenance.digitalcurrency.consortium.getMarkerTransferEvent
import io.provenance.digitalcurrency.consortium.getMigrationEvent
import io.provenance.digitalcurrency.consortium.getMintEvent
import io.provenance.digitalcurrency.consortium.getTransferEvent
import io.provenance.digitalcurrency.consortium.randomTxHash
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.provenance.digitalcurrency.consortium.service.TxRequestService
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.util.UUID

class EventStreamConsumerTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var eventStreamProperties: EventStreamProperties

    @Autowired
    private lateinit var provenanceProperties: ProvenanceProperties

    @Autowired
    private lateinit var serviceProperties: ServiceProperties

    @Autowired
    private lateinit var txRequestService: TxRequestService

    @Autowired
    lateinit var pbcServiceMock: PbcService

    private lateinit var eventStreamConsumer: EventStreamConsumer

    @BeforeEach
    fun beforeEach() {
        reset(pbcServiceMock)

        whenever(pbcServiceMock.managerAddress).thenReturn(TEST_MEMBER_ADDRESS)
    }

    @BeforeAll
    fun beforeAll() {
        eventStreamConsumer = EventStreamConsumer(
            pbcServiceMock,
            eventStreamProperties,
            serviceProperties,
            provenanceProperties,
            txRequestService,
        )
    }

    @Nested
    inner class CoinMovementEvents {
        private val mint = getMintEvent(
            dccDenom = serviceProperties.dccDenom,
        )

        private val redeem = getTransferEvent(
            toAddress = TEST_MEMBER_ADDRESS,
            denom = serviceProperties.dccDenom,
        )

        private val burn = getBurnEvent(
            memberId = TEST_MEMBER_ADDRESS,
            denom = serviceProperties.dccDenom,
        )

        private val transfer = getMarkerTransferEvent(
            toAddress = TEST_MEMBER_ADDRESS,
            denom = serviceProperties.dccDenom,
        )

        @Test
        fun `coinMovement - events without bank parties or member id are ignored`() {
            val blockTime = OffsetDateTime.now()

            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(mint.copy(memberId = "someotherbank")),
                transfers = listOf(redeem),
                burns = listOf(burn.copy(memberId = "someotherbank")),
                markerTransfers = listOf(transfer.copy(fromAddress = TEST_OTHER_MEMBER_ADDRESS, toAddress = "someotherbank")),
            )

            Assertions.assertEquals(0, transaction { CoinMovementRecord.all().count() })
        }

        @Test
        fun `coinMovement - mints, redeems, burns and transfers for bank parties are persisted`() {
            val blockTime = OffsetDateTime.now()

            transaction { insertRegisteredAddress(bankAccountUuid = UUID.randomUUID(), address = TEST_ADDRESS) }

            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(mint, mint.copy(withdrawAddress = TEST_MEMBER_ADDRESS)),
                transfers = listOf(redeem, redeem.copy(sender = TEST_ADDRESS)),
                burns = listOf(burn),
                markerTransfers = listOf(transfer, transfer.copy(fromAddress = TEST_OTHER_MEMBER_ADDRESS)),
            )

            Assertions.assertEquals(7, transaction { CoinMovementRecord.all().count() })
        }

        @Test
        fun `coinMovement - block reentry is ignored`() {
            val blockTime = OffsetDateTime.now()

            transaction { insertRegisteredAddress(bankAccountUuid = UUID.randomUUID(), address = TEST_ADDRESS) }

            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(mint),
                transfers = listOf(redeem),
                burns = listOf(burn),
                markerTransfers = listOf(transfer),
            )

            Assertions.assertEquals(4, transaction { CoinMovementRecord.all().count() })

            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(mint),
                transfers = listOf(redeem),
                burns = listOf(burn),
                markerTransfers = listOf(transfer),
            )
            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(mint),
                transfers = listOf(redeem),
                burns = listOf(burn),
                markerTransfers = listOf(transfer),
            )
            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(mint),
                transfers = listOf(redeem),
                burns = listOf(burn),
                markerTransfers = listOf(transfer),
            )

            Assertions.assertEquals(4, transaction { CoinMovementRecord.all().count() })
        }

        @Test
        fun `coinMovement - batched messages in one tx are persisted`() {
            val blockTime = OffsetDateTime.now()
            transaction { insertRegisteredAddress(bankAccountUuid = UUID.randomUUID(), address = TEST_ADDRESS) }

            eventStreamConsumer.handleCoinMovementEvents(
                mints = listOf(
                    getMintEvent(
                        txHash = "tx1",
                        dccDenom = serviceProperties.dccDenom,
                    ),
                ),
                transfers = listOf(
                    getTransferEvent(
                        txHash = "tx1",
                        toAddress = TEST_MEMBER_ADDRESS,
                        denom = serviceProperties.dccDenom,
                    ),
                ),
                burns = listOf(
                    getBurnEvent(
                        txHash = "tx1",
                        memberId = TEST_MEMBER_ADDRESS,
                        denom = serviceProperties.dccDenom,
                    ),
                ),
                markerTransfers = listOf(
                    getMarkerTransferEvent(
                        txHash = "tx1",
                        toAddress = TEST_MEMBER_ADDRESS,
                        denom = serviceProperties.dccDenom,
                    ),
                ),
            )

            Assertions.assertEquals(4, transaction { CoinMovementRecord.all().count() })
            Assertions.assertEquals(
                listOf("tx1-0", "tx1-1", "tx1-2", "tx1-3"),
                transaction { CoinMovementRecord.all().toList() }.map { it.txHash() }.sorted(),
            )
        }

        @Test
        fun `coinMovement - check v1 vs v2 output`() {
            insertCoinMovement(txHash = "abc-0", denom = serviceProperties.dccDenom)

            transaction {
                CoinMovementTable.update { it[legacyTxHash] = "abc" }
            }

            insertCoinMovement(txHash = "abc-0", denom = serviceProperties.dccDenom)
            insertCoinMovement(txHash = "xyz-0", denom = serviceProperties.dccDenom)
            insertCoinMovement(txHash = "xyz-0", denom = serviceProperties.dccDenom)
            insertCoinMovement(txHash = "xyz-1", denom = serviceProperties.dccDenom)
            insertCoinMovement(txHash = "xyz-2", denom = serviceProperties.dccDenom)

            Assertions.assertEquals(4, transaction { CoinMovementRecord.all().count() })
            Assertions.assertArrayEquals(
                listOf("abc-0", "xyz-0", "xyz-1", "xyz-2").toTypedArray(),
                transaction { CoinMovementRecord.all().toList() }.map { it._txHashV2.value }.sorted().toTypedArray(),
            )
            Assertions.assertArrayEquals(
                listOf("abc", "xyz-0", "xyz-1", "xyz-2").toTypedArray(),
                transaction { CoinMovementRecord.all().toList() }.toOutput().transactions.map { it.txId }.sorted()
                    .toTypedArray(),
            )
        }
    }

    @Nested
    inner class TxRequestEvents {
        @Test
        fun `mark tx request events as complete`() {
            val txHash = randomTxHash()
            transaction {
                insertRegisteredAddress(
                    bankAccountUuid = UUID.randomUUID(),
                    address = TEST_ADDRESS,
                    status = TxStatus.PENDING,
                    txHash = txHash,
                )
            }
            transaction {
                insertCoinMint(address = "$TEST_ADDRESS.2").also {
                    it.txHash = txHash
                    it.status = TxStatus.PENDING
                }
            }

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = emptyList(),
                migrations = emptyList(),
            )

            Assertions.assertEquals(
                1,
                transaction { AddressRegistrationRecord.find { ART.status eq TxStatus.TXN_COMPLETE }.count() },
            )

            Assertions.assertEquals(
                1,
                transaction { CoinMintRecord.find { CMT.status eq TxStatus.TXN_COMPLETE }.count() },
            )
        }
    }

    @Nested
    inner class MigrationEvents {
        @Test
        fun `event is not a migrate, tx hash does not exist, does not persist, does not process`() {
            val txHash = randomTxHash()
            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(
                    Transfer(
                        denom = "dummyDenom",
                        amount = DEFAULT_AMOUNT.toString(),
                        height = 1L,
                        dateTime = OffsetDateTime.now(),
                        txHash = txHash,
                        fromMemberId = TEST_MEMBER_ADDRESS,
                        toMemberId = TEST_MEMBER_ADDRESS,
                        sender = "sender",
                        recipient = "recipient",
                    ),
                ),
                migrations = listOf(),
            )

            transaction {
                Assertions.assertEquals(MigrationRecord.find { MT.txHash eq txHash }.count(), 0)
            }
        }

        @Test
        fun `migration hash exists, does not persist, does not process`() {
            val txHash = randomTxHash()
            insertMigration(txHash)
            val migrationEvent = getMigrationEvent(txHash)

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(),
                migrations = listOf(migrationEvent),
            )

            transaction {
                Assertions.assertEquals(MigrationRecord.find { MT.txHash eq txHash }.count(), 1)
            }
        }

        @Test
        fun `valid migration persists, processes`() {
            val txHash = randomTxHash()
            val migrationEvent = getMigrationEvent(txHash)

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(),
                migrations = listOf(migrationEvent),
            )

            transaction {
                Assertions.assertEquals(MigrationRecord.find { (MT.txHash eq txHash) and MT.sent.isNull() }.count(), 1)
            }
        }
    }

    @Nested
    inner class MarkerTransferEvents {
        @Test
        fun `event is not a transfer, tx hash, does not exist, does not persist, does not process`() {
            val txHash = randomTxHash()

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(),
                migrations = listOf(
                    Migration(
                        height = 1L,
                        dateTime = OffsetDateTime.now(),
                        txHash = txHash,
                        codeId = "2",
                    ),
                ),
            )

            transaction {
                Assertions.assertEquals(MarkerTransferRecord.find { MTT.txHash eq txHash }.count(), 0)
            }
        }

        @Test
        fun `transfer hash exists, does not persist, does not process`() {
            val txHash = randomTxHash()
            insertMarkerTransfer(txHash, denom = serviceProperties.dccDenom)
            val transferEvent = getTransferEvent(txHash, denom = serviceProperties.dccDenom)

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(transferEvent),
                migrations = listOf(),
            )

            transaction {
                Assertions.assertEquals(MarkerTransferRecord.find { MTT.txHash eq txHash }.count(), 1)
            }
        }

        @Test
        fun `recipient is not the member bank instance, does not persist, does not process`() {
            val txHash = randomTxHash()
            val transfer = getTransferEvent(txHash, "invalidrecipient", denom = serviceProperties.dccDenom)

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(transfer),
                migrations = listOf(),
            )

            transaction {
                Assertions.assertEquals(MarkerTransferRecord.find { MTT.txHash eq txHash }.count(), 0)
            }
        }

        @Test
        fun `recipient is the member bank instance, denom is not valid, does not persist, does not process`() {
            val txHash = randomTxHash()
            val transfer = getTransferEvent(txHash, denom = "invaliddenom")

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(transfer),
                migrations = listOf(),
            )

            transaction {
                Assertions.assertEquals(MarkerTransferRecord.find { MTT.txHash eq txHash }.count(), 0)
            }
        }

        @Test
        fun `valid transfer persists, processes`() {
            val txHash = randomTxHash()
            val transfer = getTransferEvent(txHash, toAddress = TEST_MEMBER_ADDRESS, denom = serviceProperties.dccDenom)

            eventStreamConsumer.handleEvents(
                txHashes = listOf(txHash),
                transfers = listOf(transfer),
                migrations = listOf(),
            )

            transaction {
                Assertions.assertEquals(
                    MarkerTransferRecord.find {
                        (MTT.txHash eq txHash) and (MTT.status eq TxStatus.TXN_COMPLETE)
                    }.count(),
                    1,
                )
            }
        }
    }
}
