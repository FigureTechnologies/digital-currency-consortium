package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import org.springframework.stereotype.Service

@Service
class CoinBurnService(private val pbcService: PbcService) {

    private val log = logger()

    fun createEvent(coinBurnRecord: CoinBurnRecord) {
        // There should not be any tx events at this point or all event should have an error status
        val existingEvents: List<TxStatusRecord> =
            TxStatusRecord.findByTxRequestUuid(coinBurnRecord.id.value)
        check(
            existingEvents.isEmpty() ||
                existingEvents.filter { it.status == TxStatus.ERROR }.size == existingEvents.size
        ) { "Burn contract already called" }

        try {
            val txResponse = pbcService.burn(amount = coinBurnRecord.coinAmount.toBigInteger()).txResponse
            log.info("Burn tx hash: ${txResponse.txhash}")
            TxStatusRecord.insert(
                txResponse = txResponse,
                txRequestUuid = coinBurnRecord.id.value,
                type = TxType.BURN_CONTRACT
            )
            CoinBurnRecord.updateStatus(coinBurnRecord.id.value, CoinBurnStatus.PENDING_BURN)
        } catch (e: Exception) {
            log.error("Burn contract failed; it will retry.", e)
        }
    }

    fun eventComplete(coinBurnRecord: CoinBurnRecord) {
        val completedEvent: TxStatusRecord? =
            TxStatusRecord.findByTxRequestUuid(coinBurnRecord.id.value).toList().firstOrNull {
                (it.status == TxStatus.COMPLETE) && (it.type == TxType.BURN_CONTRACT)
            }

        if (completedEvent != null) {
            log.info("Completing burn contract")
            CoinBurnRecord.updateStatus(coinBurnRecord.id.value, CoinBurnStatus.COMPLETE)
        } else {
            log.info("Blockchain event not completed for burn contract event yet")
        }
    }
}
