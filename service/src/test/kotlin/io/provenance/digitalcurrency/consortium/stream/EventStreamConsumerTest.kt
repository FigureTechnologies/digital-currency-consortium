package io.provenance.digitalcurrency.consortium.stream

import io.mockk.every
import io.mockk.mockkStatic
import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.DEFAULT_AMOUNT
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
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
import io.provenance.digitalcurrency.consortium.getMarkerTransferEvent
import io.provenance.digitalcurrency.consortium.getMigrationEvent
import io.provenance.digitalcurrency.consortium.getMintEvent
import io.provenance.digitalcurrency.consortium.getTransferEvent
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockId
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.PartSetHeader
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
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
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.OffsetDateTime
import java.util.UUID

class EventStreamConsumerTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var eventStreamProperties: EventStreamProperties

    @Autowired
    private lateinit var bankClientProperties: BankClientProperties

    @Autowired
    private lateinit var provenanceProperties: ProvenanceProperties

    @Autowired
    private lateinit var serviceProperties: ServiceProperties

    @Autowired
    private lateinit var txRequestService: TxRequestService

    @MockBean
    lateinit var eventStreamFactory: EventStreamFactory

    @Autowired
    lateinit var pbcServiceMock: PbcService

    @MockBean
    private lateinit var rpcClientMock: RpcClient

    private lateinit var eventStreamConsumer: EventStreamConsumer

    @BeforeEach
    fun beforeEach() {
        reset(eventStreamFactory)
        reset(pbcServiceMock)
        reset(rpcClientMock)

        whenever(pbcServiceMock.managerAddress).thenReturn(TEST_MEMBER_ADDRESS)
    }

    @BeforeAll
    fun beforeAll() {
        eventStreamConsumer = EventStreamConsumer(
            eventStreamFactory,
            pbcServiceMock,
            rpcClientMock,
            eventStreamProperties,
            serviceProperties,
            provenanceProperties,
            txRequestService
        )
    }

    @Nested
    inner class CoinMovementEvents {
        private val mint = getMintEvent(
            dccDenom = serviceProperties.dccDenom,
            bankDenom = bankClientProperties.denom
        )

        private val burn = getTransferEvent(
            toAddress = TEST_MEMBER_ADDRESS,
            denom = serviceProperties.dccDenom
        )

        private val transfer = getMarkerTransferEvent(
            toAddress = TEST_MEMBER_ADDRESS,
            denom = serviceProperties.dccDenom
        )

        @Test
        fun `coinMovement - events without bank parties are ignored`() {
            val blockTime = OffsetDateTime.now()
            val blockResponse = BlockResponse(
                block = Block(
                    header = BlockHeader(0, blockTime.toString()),
                    data = BlockData(emptyList()),
                ),
                blockId = BlockId("", PartSetHeader(0, ""))
            )

            mockkStatic(RpcClient::fetchBlock)
            every { rpcClientMock.fetchBlock(0) } returns blockResponse

            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(mint),
                burns = listOf(burn),
                transfers = listOf(transfer),
            )

            Assertions.assertEquals(0, transaction { CoinMovementRecord.all().count() })
        }

        @Test
        fun `coinMovement - mints, burns, and transfers for bank parties are persisted`() {
            val blockTime = OffsetDateTime.now()
            val blockResponse = BlockResponse(
                block = Block(
                    header = BlockHeader(0, blockTime.toString()),
                    data = BlockData(emptyList()),
                ),
                blockId = BlockId("", PartSetHeader(0, ""))
            )

            transaction { insertRegisteredAddress(bankAccountUuid = UUID.randomUUID(), address = TEST_ADDRESS) }

            mockkStatic(RpcClient::fetchBlock)
            every { rpcClientMock.fetchBlock(0) } returns blockResponse

            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(mint),
                burns = listOf(burn),
                transfers = listOf(transfer),
            )

            Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
        }

        @Test
        fun `coinMovement - block reentry is ignored`() {
            val blockTime = OffsetDateTime.now()
            val blockResponse = BlockResponse(
                block = Block(
                    header = BlockHeader(0, blockTime.toString()),
                    data = BlockData(emptyList()),
                ),
                blockId = BlockId("", PartSetHeader(0, ""))
            )

            transaction { insertRegisteredAddress(bankAccountUuid = UUID.randomUUID(), address = TEST_ADDRESS) }

            mockkStatic(RpcClient::fetchBlock)
            every { rpcClientMock.fetchBlock(0) } returns blockResponse

            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(mint),
                burns = listOf(burn),
                transfers = listOf(transfer),
            )

            Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })

            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(mint),
                burns = listOf(burn),
                transfers = listOf(transfer),
            )
            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(mint),
                burns = listOf(burn),
                transfers = listOf(transfer),
            )
            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(mint),
                burns = listOf(burn),
                transfers = listOf(transfer),
            )

            Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
        }

        @Test
        fun `coinMovement - batched messages in one tx are persisted`() {
            val blockTime = OffsetDateTime.now()
            val blockResponse = BlockResponse(
                block = Block(
                    header = BlockHeader(0, blockTime.toString()),
                    data = BlockData(emptyList()),
                ),
                blockId = BlockId("", PartSetHeader(0, ""))
            )

            transaction { insertRegisteredAddress(bankAccountUuid = UUID.randomUUID(), address = TEST_ADDRESS) }

            mockkStatic(RpcClient::fetchBlock)
            every { rpcClientMock.fetchBlock(0) } returns blockResponse

            eventStreamConsumer.handleCoinMovementEvents(
                blockHeight = blockResponse.block.header.height,
                mints = listOf(
                    getMintEvent(
                        txHash = "tx1",
                        dccDenom = serviceProperties.dccDenom,
                        bankDenom = bankClientProperties.denom
                    )
                ),
                burns = listOf(
                    getTransferEvent(
                        txHash = "tx1",
                        toAddress = TEST_MEMBER_ADDRESS,
                        denom = serviceProperties.dccDenom
                    )
                ),
                transfers = listOf(
                    getMarkerTransferEvent(
                        txHash = "tx1",
                        toAddress = TEST_MEMBER_ADDRESS,
                        denom = serviceProperties.dccDenom
                    )
                ),
            )

            Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
            Assertions.assertEquals(
                listOf("tx1-0", "tx1-1", "tx1-2"),
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
                    txHash = txHash
                )
            }
            transaction {
                insertCoinMint(address = "$TEST_ADDRESS.2").also {
                    it.txHash = txHash
                    it.status = TxStatus.PENDING
                }
            }

            eventStreamConsumer.handleEvents(
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = emptyList(),
                migrations = emptyList()
            )

            Assertions.assertEquals(
                1,
                transaction { AddressRegistrationRecord.find { ART.status eq TxStatus.TXN_COMPLETE }.count() }
            )

            Assertions.assertEquals(
                1,
                transaction { CoinMintRecord.find { CMT.status eq TxStatus.TXN_COMPLETE }.count() }
            )
        }
    }

    @Nested
    inner class MigrationEvents {
        @Test
        fun `event is not a migrate, tx hash does not exist, does not persist, does not process`() {
            val txHash = randomTxHash()
            eventStreamConsumer.handleEvents(
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(
                    Transfer(
                        denom = "dummyDenom",
                        amount = DEFAULT_AMOUNT.toString(),
                        height = 1L,
                        txHash = txHash,
                        sender = "sender",
                        recipient = "recipient"
                    )
                ),
                migrations = listOf()
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(),
                migrations = listOf(migrationEvent)
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(),
                migrations = listOf(migrationEvent)
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(),
                migrations = listOf(
                    Migration(
                        height = 1L,
                        txHash = txHash,
                        codeId = "2"
                    )
                )
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(transferEvent),
                migrations = listOf()
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(transfer),
                migrations = listOf()
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(transfer),
                migrations = listOf()
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
                blockHeight = 50,
                txHashes = listOf(txHash),
                transfers = listOf(transfer),
                migrations = listOf()
            )

            transaction {
                Assertions.assertEquals(
                    MarkerTransferRecord.find {
                        (MTT.txHash eq txHash) and (MTT.status eq TxStatus.TXN_COMPLETE)
                    }.count(),
                    1
                )
            }
        }
    }
}
