package io.provenance.digitalcurrency.consortium.stream

import com.google.protobuf.ByteString
import io.mockk.every
import io.mockk.mockkStatic
import io.provenance.attribute.v1.Attribute
import io.provenance.digitalcurrency.consortium.TestContainer
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementTable
import io.provenance.digitalcurrency.consortium.extension.toByteArray
import io.provenance.digitalcurrency.consortium.frameworks.toOutput
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockId
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.PartSetHeader
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
import io.provenance.digitalcurrency.consortium.randomTxHash
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.OffsetDateTime
import java.util.UUID

@TestContainer
class EventStreamConsumerTest {
    @Autowired
    private lateinit var eventStreamProperties: EventStreamProperties
    @Autowired
    private lateinit var bankClientProperties: BankClientProperties
    @Autowired
    private lateinit var provenanceProperties: ProvenanceProperties
    @Autowired
    private lateinit var serviceProperties: ServiceProperties

    @MockBean
    lateinit var eventStreamFactory: EventStreamFactory
    @MockBean
    lateinit var pbcServiceMock: PbcService
    @MockBean
    private lateinit var rpcClientMock: RpcClient

    private lateinit var eventStreamConsumer: EventStreamConsumer

    @BeforeEach
    fun beforeEach() {
        reset(eventStreamFactory)
        reset(pbcServiceMock)
        reset(rpcClientMock)

        transaction { CoinMovementRecord.all().map { it.delete() } }

        whenever(pbcServiceMock.managerAddress)
            .thenReturn("managerAddr")
    }

    @BeforeAll
    fun beforeAll() {
        eventStreamConsumer = EventStreamConsumer(
            eventStreamFactory,
            pbcServiceMock,
            rpcClientMock,
            bankClientProperties,
            eventStreamProperties,
            serviceProperties,
            provenanceProperties,
        )
    }

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
        val mint = Mint(
            amount = "100",
            denom = "bankcoin.1",
            withdrawDenom = "dccbank.coin",
            withdrawAddress = "toAddr",
            memberId = "bank",
            height = 0,
            txHash = randomTxHash(),
        )
        val burn = Transfer(
            contractAddress = "",
            amount = "100",
            denom = "dccbank.coin",
            recipient = pbcServiceMock.managerAddress,
            sender = "adminAddr",
            height = 0,
            txHash = randomTxHash(),
        )
        val transfer = MarkerTransfer(
            toAddress = "toAddr",
            fromAddress = "fromAddr",
            amount = "100",
            denom = "dccbank.coin",
            height = 0,
            txHash = randomTxHash(),
        )

        whenever(pbcServiceMock.getAttributeByTagName(any(), eq(bankClientProperties.kycTagName)))
            .thenReturn(null)

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
        val mint = Mint(
            amount = "100",
            denom = "bankcoin.1",
            withdrawDenom = "dccbank.coin",
            withdrawAddress = "toAddr",
            memberId = "bank",
            height = 0,
            txHash = randomTxHash(),
        )
        val burn = Transfer(
            contractAddress = "",
            amount = "100",
            denom = "dccbank.coin",
            recipient = pbcServiceMock.managerAddress,
            sender = "adminAddr",
            height = 0,
            txHash = randomTxHash(),
        )
        val transfer = MarkerTransfer(
            toAddress = "toAddr",
            fromAddress = "fromAddr",
            amount = "100",
            denom = "dccbank.coin",
            height = 0,
            txHash = randomTxHash(),
        )

        whenever(pbcServiceMock.getAttributeByTagName(any(), eq(bankClientProperties.kycTagName)))
            .thenReturn(
                Attribute.newBuilder()
                    .setName(bankClientProperties.kycTagName)
                    .setValue(ByteString.copyFrom(UUID.randomUUID().toByteArray()))
                    .build()
            )

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
        val mint = Mint(
            amount = "100",
            denom = "bankcoin.1",
            withdrawDenom = "dccbank.coin",
            withdrawAddress = "toAddr",
            memberId = "bank",
            height = 0,
            txHash = randomTxHash(),
        )
        val burn = Transfer(
            contractAddress = "",
            amount = "100",
            denom = "dccbank.coin",
            recipient = pbcServiceMock.managerAddress,
            sender = "adminAddr",
            height = 0,
            txHash = randomTxHash(),
        )
        val transfer = MarkerTransfer(
            toAddress = "toAddr",
            fromAddress = "fromAddr",
            amount = "100",
            denom = "dccbank.coin",
            height = 0,
            txHash = randomTxHash(),
        )

        whenever(pbcServiceMock.getAttributeByTagName(any(), eq(bankClientProperties.kycTagName)))
            .thenReturn(
                Attribute.newBuilder()
                    .setName(bankClientProperties.kycTagName)
                    .setValue(ByteString.copyFrom(UUID.randomUUID().toByteArray()))
                    .build()
            )

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
        val mint = Mint(
            amount = "100",
            denom = "bankcoin.1",
            withdrawDenom = "dccbank.coin",
            withdrawAddress = "toAddr",
            memberId = "bank",
            height = 0,
            txHash = "tx1",
        )
        val burn = Transfer(
            contractAddress = "",
            amount = "100",
            denom = "dccbank.coin",
            recipient = pbcServiceMock.managerAddress,
            sender = "adminAddr",
            height = 0,
            txHash = "tx1",
        )
        val transfer = MarkerTransfer(
            toAddress = "toAddr",
            fromAddress = "fromAddr",
            amount = "100",
            denom = "dccbank.coin",
            height = 0,
            txHash = "tx1",
        )

        whenever(pbcServiceMock.getAttributeByTagName(any(), eq(bankClientProperties.kycTagName)))
            .thenReturn(
                Attribute.newBuilder()
                    .setName(bankClientProperties.kycTagName)
                    .setValue(ByteString.copyFrom(UUID.randomUUID().toByteArray()))
                    .build()
            )

        mockkStatic(RpcClient::fetchBlock)
        every { rpcClientMock.fetchBlock(0) } returns blockResponse

        eventStreamConsumer.handleCoinMovementEvents(
            blockHeight = blockResponse.block.header.height,
            mints = listOf(mint),
            burns = listOf(burn),
            transfers = listOf(transfer),
        )

        Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
        Assertions.assertEquals(
            listOf("tx1-0", "tx1-1", "tx1-2"),
            transaction { CoinMovementRecord.all().toList() }.map { it.txHash() }.sorted(),
        )
    }

    @Test
    fun `coinMovement - check v1 vs v2 output`() {
        transaction {
            CoinMovementRecord.insert(
                txHash = "abc",
                fromAddress = "fromAddress",
                fromAddressBankUuid = null,
                toAddress = "toAddress",
                toAddressBankUuid = null,
                blockHeight = 0,
                blockTime = OffsetDateTime.now(),
                amount = "100",
                denom = "coin",
                type = "MINT",
            )
            CoinMovementRecord.insert(
                txHash = "abc-0",
                fromAddress = "fromAddress",
                fromAddressBankUuid = null,
                toAddress = "toAddress",
                toAddressBankUuid = null,
                blockHeight = 0,
                blockTime = OffsetDateTime.now(),
                amount = "100",
                denom = "coin",
                type = "MINT",
            )
        }

        transaction {
            CoinMovementTable.update { it[legacyTxHash] = "abc" }
        }

        transaction {
            CoinMovementRecord.insert(
                txHash = "xyz-0",
                fromAddress = "fromAddress",
                fromAddressBankUuid = null,
                toAddress = "toAddress",
                toAddressBankUuid = null,
                blockHeight = 0,
                blockTime = OffsetDateTime.now(),
                amount = "100",
                denom = "coin",
                type = "MINT",
            )
            CoinMovementRecord.insert(
                txHash = "xyz-0",
                fromAddress = "fromAddress",
                fromAddressBankUuid = null,
                toAddress = "toAddress",
                toAddressBankUuid = null,
                blockHeight = 0,
                blockTime = OffsetDateTime.now(),
                amount = "100",
                denom = "coin",
                type = "MINT",
            )
            CoinMovementRecord.insert(
                txHash = "xyz-1",
                fromAddress = "fromAddress",
                fromAddressBankUuid = null,
                toAddress = "toAddress",
                toAddressBankUuid = null,
                blockHeight = 0,
                blockTime = OffsetDateTime.now(),
                amount = "100",
                denom = "coin",
                type = "MINT",
            )
            CoinMovementRecord.insert(
                txHash = "xyz-2",
                fromAddress = "fromAddress",
                fromAddressBankUuid = null,
                toAddress = "toAddress",
                toAddressBankUuid = null,
                blockHeight = 0,
                blockTime = OffsetDateTime.now(),
                amount = "100",
                denom = "coin",
                type = "MINT",
            )
        }

        Assertions.assertEquals(5, transaction { CoinMovementRecord.all().count() })
        Assertions.assertArrayEquals(
            listOf("abc", "abc-0", "xyz-0", "xyz-1", "xyz-2").toTypedArray(),
            transaction { CoinMovementRecord.all().toList() }.map { it._txHashV2.value }.sorted().toTypedArray(),
        )
        Assertions.assertArrayEquals(
            listOf("abc", "abc", "xyz-0", "xyz-1", "xyz-2").toTypedArray(),
            transaction { CoinMovementRecord.all().toList() }.toOutput().transactions.map { it.txId }.sorted().toTypedArray(),
        )
    }
}
