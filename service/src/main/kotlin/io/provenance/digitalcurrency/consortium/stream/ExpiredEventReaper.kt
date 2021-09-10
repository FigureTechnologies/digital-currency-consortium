package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@NotTest
@Component
class ExpiredEventReaper(private val pbcService: PbcService) {

    private val log = logger()

    @Scheduled(initialDelayString = "\${event_stream.expiration.initial_delay.ms}", fixedDelayString = "\${event_stream.expiration.delay.ms}")
    fun pollExpiredTransactions() {
        val uuids = transaction { TxStatusRecord.findByExpired().map { it.id.value } }

        uuids.forEach { uuid ->
            log.info("Handling $uuid from expired transaction reaper")
            transaction {
                val txStatusRecord = TxStatusRecord.findForUpdate(uuid).first()
                if (txStatusRecord.status != TxStatus.PENDING)
                    return@transaction

                val txQuery = pbcService.getTransaction(txStatusRecord.txHash)
                when {
                    txQuery!!.txResponse.isFailed() -> txStatusRecord.setStatus(
                        TxStatus.ERROR,
                        txQuery.txResponse.rawLog
                    )
                    txQuery.txResponse.height > 0 -> txStatusRecord.setStatus(TxStatus.COMPLETE)
                    else -> txStatusRecord
                }.also {
                    log.info("Expired reaper found tx event for tx status uuid:$uuid with status:${it.status}")
                }
            }
        }
    }
}
