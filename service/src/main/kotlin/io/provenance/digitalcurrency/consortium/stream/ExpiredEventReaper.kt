package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.isSuccess
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.provenance.digitalcurrency.consortium.service.TxRequestService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@NotTest
@Component
class ExpiredEventReaper(private val pbcService: PbcService, private val txRequestService: TxRequestService) {

    private val log = logger()

    @Scheduled(
        initialDelayString = "\${event_stream.expiration.initial_delay.ms}",
        fixedDelayString = "\${event_stream.expiration.delay.ms}"
    )
    fun pollExpiredTransactions() {
        transaction { TxRequestViewRecord.findExpired().groupBy { it.txHash } }.forEach { (txHash, uuids) ->
            log.info("Handling $txHash for ids $uuids from expired transaction reaper")

            val response = pbcService.getTransaction(txHash!!)
            when {
                response == null -> log.info("no tx response, wait?")
                response.txResponse.isSuccess() -> txRequestService.completeTxns(txHash)
                response.txResponse.isFailed() -> {
                    log.error("Unexpected error for tx:$txHash, log:${response.txResponse.rawLog}")
                    txRequestService.resetTxns(txHash)
                }
            }
        }
    }
}
