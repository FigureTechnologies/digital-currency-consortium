package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
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
@NotTest
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
            CoinRedemptionRecord.findPendingForUpdate(message.id).first().let { coinRedemption ->
                withMdc(*coinRedemption.mdc()) {
                    when (coinRedemption.status) {
                        TxStatus.QUEUED -> coinRedemptionService.createEvent(coinRedemption)
                        TxStatus.TXN_COMPLETE -> coinRedemptionService.eventComplete(coinRedemption)
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
    }
}
