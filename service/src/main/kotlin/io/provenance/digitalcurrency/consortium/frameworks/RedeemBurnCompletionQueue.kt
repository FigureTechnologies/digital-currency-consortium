package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinRedeemBurnRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.mdc
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
class RedeemBurnCompletionQueue(
    private val bankClient: BankClient,
    coroutineProperties: CoroutineProperties
) :
    ActorModel<CoinRedemptionDirective, CoinRedemptionOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start redemption queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<CoinRedemptionDirective> =
        transaction {
            CoinRedeemBurnRecord.findTxnCompleted().map { CoinRedemptionDirective(it.id.value) }
        }

    override fun processMessage(message: CoinRedemptionDirective): CoinRedemptionOutcome {
        transaction {
            CoinRedeemBurnRecord.findTxnCompletedForUpdate(message.id).first().let { coinRedemptionBurn ->
                withMdc(*coinRedemptionBurn.mdc()) {
                    log.info("Completing redeem and burn contract by notifying bank")
                    try {
                        bankClient.completeBurn(coinRedemptionBurn.id.value)
                        CoinRedeemBurnRecord.updateStatus(coinRedemptionBurn.id.value, TxStatus.ACTION_COMPLETE)
                    } catch (e: Exception) {
                        log.error("updating mint status at bank failed; it will retry.", e)
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
