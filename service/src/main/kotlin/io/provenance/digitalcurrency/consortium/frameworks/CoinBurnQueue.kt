package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnStatus
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.CoinBurnService
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
    private val coinBurnService: CoinBurnService
) :
    ActorModel<CoinBurnDirective, CoinBurnOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start burn queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<CoinBurnDirective> =
        transaction {
            CoinBurnRecord.findPending().map { CoinBurnDirective(it.id.value) }
        }

    override fun processMessage(message: CoinBurnDirective): CoinBurnOutcome {
        transaction {
            CoinBurnRecord.findForUpdate(message.id).first().let { coinBurn ->
                withMdc(*coinBurn.mdc()) {
                    check(
                        coinBurn.status == CoinBurnStatus.INSERTED ||
                            coinBurn.status == CoinBurnStatus.PENDING_BURN
                    ) { "Invalid coin burn status for queue processing" }

                    when (coinBurn.status) {
                        CoinBurnStatus.INSERTED -> coinBurnService.createEvent(coinBurn)
                        CoinBurnStatus.PENDING_BURN -> coinBurnService.eventComplete(coinBurn)
                        else -> log.error("Invalid status - should never get here")
                    }
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
