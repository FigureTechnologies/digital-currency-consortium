package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintStatus
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.CoinMintService
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
    private val coinMintService: CoinMintService
) :
    ActorModel<CoinMintDirective, CoinMintOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start mint queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<CoinMintDirective> =
        transaction {
            CoinMintRecord.findPending().map { CoinMintDirective(it.id.value) }
        }

    override fun processMessage(message: CoinMintDirective): CoinMintOutcome {
        transaction {
            CoinMintRecord.findForUpdate(message.id).first().let { coinMint ->
                withMdc(*coinMint.mdc()) {
                    check(
                        coinMint.status == CoinMintStatus.INSERTED ||
                            coinMint.status == CoinMintStatus.PENDING_MINT
                    ) { "Invalid coin mint status for queue processing" }

                    when (coinMint.status) {
                        CoinMintStatus.INSERTED -> coinMintService.createEvent(coinMint)
                        CoinMintStatus.PENDING_MINT -> coinMintService.eventComplete(coinMint)
                        else -> log.error("Invalid status - should never get here")
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
        transaction { CoinMintRecord.updateStatus(message.id, CoinMintStatus.EXCEPTION) }
    }
}
