package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.mdc
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
@NotTest
class BurnCompletionQueue(
    private val bankClient: BankClient,
    coroutineProperties: CoroutineProperties
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
            CoinBurnRecord.findTxnCompleted().map { CoinBurnDirective(it.id.value) }
        }

    override fun processMessage(message: CoinBurnDirective): CoinBurnOutcome {
        transaction {
            CoinBurnRecord.findTxnCompletedForUpdate(message.id).first().let { coinRedemptionBurn ->
                withMdc(*coinRedemptionBurn.mdc()) {
                    log.info("Completing burn contract by notifying bank")
                    try {
                        bankClient.completeBurn(coinRedemptionBurn.id.value)
                        CoinBurnRecord.updateStatus(coinRedemptionBurn.id.value, TxStatus.ACTION_COMPLETE)
                    } catch (e: Exception) {
                        log.error("updating burn status at bank failed; it will retry.", e)
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
