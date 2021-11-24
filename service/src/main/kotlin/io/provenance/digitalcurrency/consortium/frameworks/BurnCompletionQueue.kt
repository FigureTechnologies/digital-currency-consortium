package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class CoinBurnDirective(
    override val id: UUID
) : Directive()

class CoinBurnOutcome(
    override val id: UUID
) : Outcome()

@Component
class CoinBurnQueue(
    coroutineProperties: CoroutineProperties,
    private val pbcService: PbcService,
) :
    ActorModel<CoinBurnDirective, CoinBurnOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start burn queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<CoinBurnDirective> =
        transaction {
            CoinBurnRecord.findPending().map { CoinBurnDirective(it.id.value) }
        }

    override fun processMessage(message: CoinBurnDirective): CoinBurnOutcome {
        transaction {
            CoinBurnRecord.findPendingForUpdate(message.id).first().let { coinBurn ->
                withMdc(*coinBurn.mdc()) {
                    pbcService.getTransaction(coinBurn.txHash!!)?.let { response ->
                        when (response.txResponse.isFailed()) {
                            true -> {
                                log.info("burn failed, need to retry")
                                coinBurn.resetForRetry()
                            }
                            false -> {
                                CoinBurnRecord.updateStatus(coinBurn.id.value, TxStatus.COMPLETE)
                            }
                        }
                    } ?: log.info("burn blockchain request not complete. Will retry.")
                }
            }
        }
        return CoinBurnOutcome(message.id)
    }

    override fun onMessageSuccess(result: CoinBurnOutcome) {
        log.info("burn queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: CoinBurnDirective, e: Exception) {
        log.error("burn queue got error for uuid ${message.id}", e)
    }
}
