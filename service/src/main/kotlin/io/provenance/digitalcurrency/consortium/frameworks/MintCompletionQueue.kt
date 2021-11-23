package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class CoinMintDirective(
    override val id: UUID
) : Directive()

class CoinMintOutcome(
    override val id: UUID
) : Outcome()

@Component
class CoinMintQueue(
    coroutineProperties: CoroutineProperties,
    private val pbcService: PbcService,
    private val bankClient: BankClient
) : ActorModel<CoinMintDirective, CoinMintOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start mint queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<CoinMintDirective> =
        transaction {
            CoinMintRecord.findPendingWithTxHash().map { CoinMintDirective(it.id.value) }
        }

    override fun processMessage(message: CoinMintDirective): CoinMintOutcome {
        transaction {
            CoinMintRecord.findPendingForUpdate(message.id).first().let { coinMint ->
                withMdc(*coinMint.mdc()) {
                    val response = pbcService.getTransaction(coinMint.txHash!!)!!.txResponse

                    when (response == null || response.isFailed()) {
                        true -> {
                            log.info("mint failed, need to retry")
                            coinMint.resetForRetry()
                        }
                        false -> {
                            log.info("Completing mint contract by notifying bank")
                            try {
                                bankClient.completeMint(coinMint.id.value)
                                CoinMintRecord.updateStatus(coinMint.id.value, TxStatus.COMPLETE)
                            } catch (e: Exception) {
                                log.error("updating mint status at bank failed; it will retry.", e)
                            }
                        }
                    }
                }
            }
        }
        return CoinMintOutcome(message.id)
    }

    override fun onMessageSuccess(result: CoinMintOutcome) {
        log.info("mint queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: CoinMintDirective, e: Exception) {
        log.error("mint queue got error for uuid ${message.id}", e)
    }
}
