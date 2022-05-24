package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.isSuccess
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.provenance.digitalcurrency.consortium.service.TxRequestService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// prod is 6.15s as of 5/24/2022
private const val AVG_BLOCK_CUT_TIME = 7

@NotTest
@Component
class ExpiredEventReaper(
    private val pbcService: PbcService,
    private val txRequestService: TxRequestService,
    provenanceProperties: ProvenanceProperties
) {

    private val expiredSecondsTimeout = provenanceProperties.blocksBeforeTimeout.toLong() * AVG_BLOCK_CUT_TIME
    private val log = logger()

    @Scheduled(
        initialDelayString = "\${event_stream.expiration.initial_delay.ms}",
        fixedDelayString = "\${event_stream.expiration.delay.ms}"
    )
    fun pollExpiredTransactions() {
        transaction { TxRequestViewRecord.findExpired(expiredSecondsTimeout).groupBy { it.txHash } }.forEach { (txHash, uuids) ->
            log.info("Handling $txHash for ids $uuids from expired transaction reaper")

            val response = pbcService.getTransaction(txHash!!)
            when {
                response == null -> log.info("no tx response, wait?")
                response.txResponse.isSuccess() -> txRequestService.completeTxns(txHash)
                response.txResponse.isFailed() -> {
                    log.error("Unexpected error for tx:$txHash, log:${response.txResponse.rawLog}")
                    // TODO - determine if event stream error handling will catch all errors so this is no longer needed
                    // txRequestService.resetTxns(txHash)
                }
            }
        }
    }
}
