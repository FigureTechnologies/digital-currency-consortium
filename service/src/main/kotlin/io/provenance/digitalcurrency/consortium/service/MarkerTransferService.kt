package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import org.springframework.stereotype.Service

@Service
class MarkerTransferService(val bankClient: BankClient) {
    private val log by lazy { logger() }

    fun createEvent(markerTransferRecord: MarkerTransferRecord) {
        // if (coinRedemptionRecord.status != CoinRedemptionStatus.BANK_REQUEST_COMPLETED) {
        //     log.error("Unexpected coin redemption status for creating event")
        //     coinRedemptionRecord.updateStatus(CoinRedemptionStatus.VALIDATION_FAILED)
        //     return
        // }
        // // There should not be any tx events at this point or all event should have an error status
        // val existingEvents: List<TxStatusRecord> =
        //     TxStatusRecord.findByTxRequestUuid(coinRedemptionRecord.id.value).toList()
        // if (existingEvents.firstOrNull() != null &&
        //     existingEvents.filter { it.status == TxStatus.ERROR }.size != existingEvents.size
        // ) {
        //     log.error("Burning event already exists")
        //     coinRedemptionRecord.updateStatus(CoinRedemptionStatus.VALIDATION_FAILED)
        //     return
        // }
        //
        // try {
        //     val txReply = pbcService.burn(coinRedemptionRecord.coinAmount.toBigInteger())
        //     log.info("Burn tx hash: ${txReply.txResponse.txhash}")
        //     TxStatusRecord.insert(coinRedemptionRecord.id.value, txReply.txResponse, TxType.MARKER_BURN)
        //     coinRedemptionRecord.updateStatus(CoinRedemptionStatus.PENDING_BURN)
        // } catch (e: Exception) {
        //     log.error("Blockchain burn failed. Will retry; no status update needed.", e)
        // }
    }

    fun eventComplete(markerTransferRecord: MarkerTransferRecord) {
        // if (coinRedemptionRecord.status != CoinRedemptionStatus.PENDING_BURN) {
        //     log.error("Unexpected coin redemption status for completing redemption uuid ${coinRedemptionRecord.id.value}")
        //     coinRedemptionRecord.updateStatus(CoinRedemptionStatus.VALIDATION_FAILED)
        //     return
        // }
        //
        // val completedEvent: TxStatusRecord? =
        //     TxStatusRecord.findByTxRequestUuid(coinRedemptionRecord.id.value).toList().firstOrNull {
        //         (it.status == TxStatus.COMPLETE) && (it.type == TxType.MARKER_BURN)
        //     }
        //
        // if (completedEvent != null) {
        //     log.info("Completing redemption")
        //     try {
        //         val updatedDate = OffsetDateTime.now()
        //         bankClient.updateStatus(
        //             coinRedemptionRecord.id.value,
        //             CoinStatusUpdateRequest(
        //                 uuid = coinRedemptionRecord.id.value,
        //                 status = CoinStatus.COMPLETE,
        //                 updatedDate = updatedDate
        //             )
        //         )
        //         coinRedemptionRecord.updateStatus(CoinRedemptionStatus.COMPLETE, updatedDate)
        //     } catch (e: Exception) {
        //         log.error("updating bank status failed. Will retry; no status update needed.", e)
        //     }
        // } else {
        //     log.info("Blockchain event not completed for burn event yet")
        // }
    }
}
