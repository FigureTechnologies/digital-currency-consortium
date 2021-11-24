package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.coinsAmount
import io.provenance.digitalcurrency.consortium.extension.findWithdrawEvent
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.PbcService
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
    private val pbcService: PbcService,
    private val bankClient: BankClient,
    bankClientProperties: BankClientProperties,
) :
    ActorModel<CoinRedemptionDirective, CoinRedemptionOutcome> {
    private val log = logger()
    private val bankDenom = bankClientProperties.denom

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start redemption queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<CoinRedemptionDirective> =
        transaction {
            CoinRedemptionRecord.findPending().map { CoinRedemptionDirective(it.id.value) }
        }

    override fun processMessage(message: CoinRedemptionDirective): CoinRedemptionOutcome {
        transaction {
            CoinRedemptionRecord.findPendingForUpdate(message.id).first().let { coinRedemption ->
                withMdc(*coinRedemption.mdc()) {
                    pbcService.getTransaction(coinRedemption.txHash!!)?.let { response ->
                        when (response.txResponse.isFailed()) {
                            true -> {
                                log.info("redeem failed, need to retry")
                                coinRedemption.resetForRetry()
                            }
                            false -> {
                                log.info("Completing redemption, setting up the burn")
                                bankClient.depositFiat(
                                    DepositFiatRequest(
                                        uuid = coinRedemption.id.value,
                                        bankAccountUUID = coinRedemption.addressRegistration.bankAccountUuid,
                                        amount = coinRedemption.fiatAmount
                                    )
                                )

                                // If we redeemed bank-specific coin, initialize burn for bank-specific portion
                                response.txResponse.logsList.first().findWithdrawEvent(bankDenom)?.run {
                                    CoinBurnRecord.insert(coinRedemption = coinRedemption, coinAmount = coinsAmount(bankDenom)).also {
                                        log.info("Setting up burn of ${it.coinAmount}")
                                    }
                                }
                                try {
                                    CoinBurnRecord.insert(
                                        coinRedemption = coinRedemption,
                                        coinAmount = coinRedemption.coinAmount
                                    )
                                    CoinRedemptionRecord.updateStatus(coinRedemption.id.value, TxStatus.COMPLETE)
                                } catch (e: Exception) {
                                    log.error("prepping for burn failed; it will retry.", e)
                                }
                            }
                        }
                    } ?: log.info("redemption blockchain request not complete. Will retry.")
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
