package io.provenance.digitalcurrency.consortium.stream

import com.nhaarman.mockitokotlin2.reset
import io.provenance.digitalcurrency.consortium.TestContainer
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean

@TestContainer
class EventStreamConsumerTest(
    private val serviceProperties: ServiceProperties
) {
    @Autowired
    private lateinit var eventStreamProperties: EventStreamProperties

    @MockBean
    private lateinit var eventStreamFactory: EventStreamFactory

    @MockBean
    lateinit var pbcService: PbcService

    class EventStreamConsumerWrapper(
        eventStreamFactory: EventStreamFactory,
        pbcService: PbcService,
        eventStreamProperties: EventStreamProperties,
        serviceProperties: ServiceProperties
    ) : EventStreamConsumer(
        eventStreamFactory,
        pbcService,
        eventStreamProperties,
        serviceProperties
    ) {
        fun testHandleEvents(burns: Burns = listOf(), withdraws: Withdraws = listOf(), markerTransfers: MarkerTransfers = listOf()) {
            super.handleEvents(0L, burns, withdraws, markerTransfers)
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
            eventStreamProperties,
            serviceProperties
        )
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
