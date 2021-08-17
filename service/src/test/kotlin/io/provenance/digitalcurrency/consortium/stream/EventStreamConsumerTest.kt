package io.provenance.digitalcurrency.consortium.stream

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import io.provenance.digitalcurrency.consortium.TestContainer
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockId
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.PartSetHeader
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
import io.provenance.digitalcurrency.consortium.randomTxHash
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.OffsetDateTime

@TestContainer
class EventStreamConsumerTest(
    private val serviceProperties: ServiceProperties
) {
    @Autowired
    private lateinit var eventStreamProperties: EventStreamProperties
    @Autowired
    private lateinit var bankClientProperties: BankClientProperties

    @MockBean
    private lateinit var eventStreamFactory: EventStreamFactory

    @MockBean
    lateinit var pbcService: PbcService
    lateinit var rpcClient: RpcClient

    class EventStreamConsumerWrapper(
        eventStreamFactory: EventStreamFactory,
        pbcService: PbcService,
        rpcClient: RpcClient,
        bankClientProperties: BankClientProperties,
        eventStreamProperties: EventStreamProperties,
        serviceProperties: ServiceProperties
    ) : EventStreamConsumer(
        eventStreamFactory,
        pbcService,
        rpcClient,
        bankClientProperties,
        eventStreamProperties,
        serviceProperties
    ) {
        fun testHandleEvents(burns: Burns = listOf(), withdraws: Withdraws = listOf(), markerTransfers: MarkerTransfers = listOf(), mints: Mints = listOf()) {
            super.handleEvents(0L, burns, withdraws, markerTransfers, mints)
        }

        fun testHandleCoinMovementEvents(burns: Burns = listOf(), withdraws: Withdraws = listOf(), markerTransfers: MarkerTransfers = listOf(), mints: Mints = listOf()) {
            super.handleEvents(0L, burns, withdraws, markerTransfers, mints)
        }
    }

    private lateinit var eventStreamConsumerWrapper: EventStreamConsumerWrapper

    @BeforeEach
    fun beforeEach() {
        reset(eventStreamFactory)
    }

    @BeforeAll
    fun beforeAll() {
        eventStreamConsumerWrapper = EventStreamConsumerWrapper(
            eventStreamFactory,
            pbcService,
            rpcClient,
            bankClientProperties,
            eventStreamProperties,
            serviceProperties
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
            txHash = randomTxHash(),
            toAddress = "toAddr",
            administrator = "adminAddr",
            coins = "100",
            denom = "usdf.c",
            height = 0,
        )
        val transfer = MarkerTransfer(
            txHash = randomTxHash(),
            toAddress = "toAddr",
            fromAddress = "fromAddr",
            amount = "100",
            denom = "usdf.c",
            height = 0,
        )
        val withdraw = Withdraw(
            txHash = randomTxHash(),
            toAddress = "toAddr",
            administrator = "adminAddr",
            coins = "100",
            denom = "usdf.c",
            height = 0,
        )

        whenever(rpcClient.fetchBlock(any())).thenReturn(blockResponse)
        whenever(pbcService.getAttributes(any())).thenReturn(emptyList())

        eventStreamConsumerWrapper.testHandleEvents(
            mints = listOf(mint),
            withdraws = listOf(withdraw),
            markerTransfers = listOf(transfer),
        )

        Assertions.assertEquals(0, transaction { CoinMovementRecord.all().count() })
    }

    @Test
    fun `coinMovement - block reentry is ignored`() {

    }

    @Test
    fun `coinMovement - mints, withraws, and transfers for bank parties are persisted`() {

    }

    // @Test
    // fun `event is not a transfer, does not persist, does not process`() {
    //     val txHash = randomTxHash()
    //     val txResponseSuccess = generateTxResponseFail(txHash)
    //
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseSuccess)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(
    //         burns = listOf(
    //             Burn(
    //                 burnedBy = "dummyBurner",
    //                 denom = "dummyDenom",
    //                 amount = DEFAULT_AMOUNT.toString(),
    //                 height = 1L,
    //                 txHash = txHash
    //             )
    //         )
    //     )
    //
    //     verify(pbcService, never()).withdraw(any(), any(), isNull())
    //
    //     transaction {
    //         assertEquals(TxStatusRecord.find { TxStatusTable.txHash eq txHash }.count(), 0)
    //         assertEquals(PendingTransferRecord.find { PendingTransferTable.txHash eq txHash }.count(), 0)
    //     }
    // }
    //
    // @Test
    // fun `receipt hash exists, does not persist, does not process`() {
    //     val txHash = randomTxHash()
    //     val receipt = insertCoinReceipt()
    //     insertTxStatus(receipt.id.value, txHash, TxType.TRANSFER, TxStatus.COMPLETE)
    //     val transfer = generateTransferEvent(txHash)
    //     val txResponseSuccess = generateTxResponseSuccess(txHash)
    //
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseSuccess)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //
    //     verify(pbcService, never()).withdraw(any(), any(), isNull())
    //
    //     transaction {
    //         assertEquals(TxStatusRecord.find { TxStatusTable.txHash eq txHash }.count(), 1)
    //         assertEquals(PendingTransferRecord.find { PendingTransferTable.txHash eq txHash }.count(), 0)
    //     }
    // }
    //
    // @Test
    // fun `recipient is not the omnibus instance, does not persist, does not process`() {
    //     val txHash = randomTxHash()
    //     val transfer = generateTransferEvent(txHash, "invalidrecipient")
    //     val txResponseSuccess = generateTxResponseFail(txHash)
    //
    //     whenever(pbcService.stablecoinAddress).thenReturn(VALID_RECIPIENT)
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseSuccess)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //
    //     verify(pbcService, never()).withdraw(any(), any(), isNull())
    //
    //     transaction {
    //         assertEquals(TxStatusRecord.find { TxStatusTable.txHash eq txHash }.count(), 0)
    //         assertEquals(PendingTransferRecord.find { PendingTransferTable.txHash eq txHash }.count(), 0)
    //     }
    // }
    //
    // @Test
    // fun `valid receipt persists, processes`() {
    //     val txHash = randomTxHash()
    //     val transfer = generateTransferEvent(txHash)
    //     val txResponseSuccess = generateTxResponseSuccess(txHash)
    //
    //     whenever(pbcService.stablecoinAddress).thenReturn(VALID_RECIPIENT)
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseSuccess)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //
    //     verify(pbcService, never()).withdraw(any(), any(), isNull())
    //
    //     transaction {
    //         assertEquals(TxStatusRecord.find { TxStatusTable.txHash eq txHash }.count(), 0)
    //         assertEquals(
    //             PendingTransferRecord.find {
    //                 (PendingTransferTable.txHash eq txHash) and (PendingTransferTable.status eq PendingTransferStatus.INSERTED)
    //             }.count(),
    //             1
    //         )
    //     }
    // }
    //
    // @Test
    // fun `valid withdraw receipt persists, processes`() {
    //     val txHash = randomTxHash()
    //     val transfer = generateTransferEvent(txHash)
    //     val txResponseSuccess = generateTxResponseSuccess(txHash)
    //
    //     whenever(pbcService.stablecoinAddress).thenReturn(VALID_RECIPIENT)
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseSuccess)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //
    //     verify(pbcService, never()).withdraw(any(), any(), isNull())
    //
    //     transaction {
    //         assertEquals(TxStatusRecord.find { TxStatusTable.txHash eq txHash }.count(), 0)
    //         assertEquals(
    //             PendingTransferRecord.find {
    //                 (PendingTransferTable.txHash eq txHash) and (PendingTransferTable.status eq PendingTransferStatus.INSERTED)
    //             }.count(),
    //             1
    //         )
    //     }
    // }
    //
    // @Test
    // fun `tx failed, don't persist coin receipt`() {
    //     val txHash = randomTxHash()
    //     val transfer = generateTransferEvent(txHash)
    //     val txResponseFail = generateTxResponseFail(txHash)
    //
    //     whenever(pbcService.stablecoinAddress).thenReturn(VALID_RECIPIENT)
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseFail)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //
    //     transaction {
    //         assertEquals(TxStatusRecord.find { TxStatusTable.txHash eq txHash }.count(), 0)
    //         assertEquals(
    //             PendingTransferRecord.find {
    //                 (PendingTransferTable.txHash eq txHash) and (PendingTransferTable.status eq PendingTransferStatus.INSERTED)
    //             }.count(),
    //             0
    //         )
    //     }
    // }
    //
    // @Test
    // fun `tx failed, receipt hash exists, update status to ERROR`() {
    //     val txHash = randomTxHash()
    //     val receipt = insertCoinReceipt()
    //     insertTxStatus(receipt.id.value, txHash, TxType.TRANSFER, TxStatus.PENDING)
    //     val transfer = generateTransferEvent(txHash)
    //     val txResponseFail = generateTxResponseFail(txHash)
    //
    //     whenever(pbcService.getTransaction(any())).thenReturn(txResponseFail)
    //
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //
    //     transaction {
    //         val txStatusResults = TxStatusRecord.find { TxStatusTable.txHash eq txHash }
    //         assertEquals(txStatusResults.count(), 1)
    //         assertEquals(TxStatus.ERROR, txStatusResults.firstOrNull()?.status)
    //         assertEquals(PendingTransferRecord.find { PendingTransferTable.txHash eq txHash }.count(), 0)
    //     }
    // }
    //
    // @Test
    // fun `txHash is null or empty, skip, don't process`() {
    //     var transfer = generateTransferEvent("")
    //     eventStreamConsumerWrapper.testHandleEvents(transfers = listOf(transfer))
    //     // should just exit without any processing, no exceptions raised due to logic skipped
    //     assert(true)
    // }
}
