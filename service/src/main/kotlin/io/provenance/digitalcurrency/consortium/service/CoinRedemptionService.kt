package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import org.springframework.stereotype.Service

@Service
class CoinRedemptionService(private val pbcService: PbcService) {
    private val log = logger()

    fun createEvent(coinRedemptionRecord: CoinRedemptionRecord) {
        // There should not be any tx events at this point or all event should have an error status
        val existingEvents: List<TxStatusRecord> =
            TxStatusRecord.findByTxRequestUuid(coinRedemptionRecord.id.value)
        check(
            existingEvents.isEmpty() ||
                existingEvents.filter { it.status == TxStatus.ERROR }.size == existingEvents.size
        ) { "Redemption event already exists" }

        try {
            val txResponse = pbcService.redeem(coinRedemptionRecord.coinAmount.toBigInteger()).txResponse
            log.info("Redeem tx hash: ${txResponse.txhash}")
            TxStatusRecord.insert(
                txResponse = txResponse,
                txRequestUuid = coinRedemptionRecord.id.value,
                type = TxType.MARKER_REDEEM
            )
            CoinRedemptionRecord.updateStatus(coinRedemptionRecord.id.value, CoinRedemptionStatus.PENDING_REDEEM)
        } catch (e: Exception) {
            log.error("redeem contract failed; it will retry.", e)
        }
    }

    fun eventComplete(coinRedemptionRecord: CoinRedemptionRecord) {
        val completedEvent: TxStatusRecord? =
            TxStatusRecord.findByTxRequestUuid(coinRedemptionRecord.id.value).toList().firstOrNull {
                (it.status == TxStatus.COMPLETE) && (it.type == TxType.MARKER_REDEEM)
            }

        if (completedEvent != null) {
            log.info("Completing redemption, setting up the burn")
            try {
                CoinBurnRecord.insert(
                    coinRedemption = coinRedemptionRecord,
                    coinAmount = coinRedemptionRecord.coinAmount
                )
                CoinRedemptionRecord.updateStatus(coinRedemptionRecord.id.value, CoinRedemptionStatus.COMPLETE)
            } catch (e: Exception) {
                log.error("prepping for burn failed; it will retry.", e)
            }
        } else {
            log.info("Blockchain event not completed for redemption contract event yet")
        }
    }
}
