package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.CoinRedemptionService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class CoinRedemptionDirective(
    override val id: UUID
) : Directive()

class CoinRedemptionOutcome(
    override val id: UUID
) : Outcome()

@Component
class CoinRedemptionQueue(
    coroutineProperties: CoroutineProperties,
    private val coinRedemptionService: CoinRedemptionService
) :
    ActorModel<CoinRedemptionDirective, CoinRedemptionOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start redemption queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<CoinRedemptionDirective> =
        transaction {
            CoinRedemptionRecord.findPending().map { CoinRedemptionDirective(it.id.value) }
        }

    override fun processMessage(message: CoinRedemptionDirective): CoinRedemptionOutcome {
        transaction {
            CoinRedemptionRecord.findForUpdate(message.id).first().let { coinRedemption ->
                withMdc(*coinRedemption.mdc()) {
                    check(
                        coinRedemption.status == CoinRedemptionStatus.INSERTED ||
                            coinRedemption.status == CoinRedemptionStatus.PENDING_REDEEM
                    ) { "Invalid coin redemption status for queue processing" }

                    when (coinRedemption.status) {
                        CoinRedemptionStatus.INSERTED -> coinRedemptionService.createEvent(coinRedemption)
                        CoinRedemptionStatus.PENDING_REDEEM -> coinRedemptionService.eventComplete(coinRedemption)
                        else -> log.error("Invalid status - should never get here")
                    }
                }
            }
        }
        return CoinRedemptionOutcome(message.id)
    }

    override fun onMessageSuccess(result: CoinRedemptionOutcome) {
        log.info("redemption queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: CoinRedemptionDirective, e: Exception) {
        log.error("redemption queue got error for uuid ${message.id}", e)
        transaction {
            CoinRedemptionRecord.updateStatus(message.id, CoinRedemptionStatus.EXCEPTION)
        }
    }
}
