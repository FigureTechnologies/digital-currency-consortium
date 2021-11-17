package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintStatus
import io.provenance.digitalcurrency.consortium.service.CoinMintService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class CoinMintBatchDirective(
    override val ids: List<UUID>
) : BatchDirective()

class CoinMintBatchOutcome(
    override val ids: List<UUID>
) : BatchOutcome()

@Component
class CoinMintBatchQueue(
    coroutineProperties: CoroutineProperties,
    private val coinMintService: CoinMintService
) : BatchActorModel<CoinMintBatchDirective, CoinMintBatchOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start mint batch queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val batchSize: Int = coroutineProperties.batchSize
    override val pollingDelayMillis: Long = coroutineProperties.batchPollingDelayMs

    override suspend fun loadMessages(): CoinMintBatchDirective =
        transaction {
            log.info("Grabbing $batchSize coins to mint")
            CoinMintBatchDirective(CoinMintRecord.findNew(batchSize).map { it.id.value })
        }

    override fun processMessages(messages: CoinMintBatchDirective): CoinMintBatchOutcome {
        val processedIds = transaction {
            val actualCoinsToMint = CoinMintRecord.findAllForUpdate(messages.ids).let { coinsToMint ->
                coinsToMint.dropWhile { coinMintRecord ->
                    log.error("Received coin mint with invalid status ${coinMintRecord.status} - not handling in batch queue")
                    coinMintRecord.status != CoinMintStatus.INSERTED
                }
            }
            coinMintService.createEvent(actualCoinsToMint)
            actualCoinsToMint.map { it.id.value }
        }
        return CoinMintBatchOutcome(processedIds)
    }

    override fun onMessagesSuccess(result: CoinMintBatchOutcome) {
        log.info("mint batch queue successfully processed uuids $result.")
    }

    override fun onMessagesFailure(messages: CoinMintBatchDirective, e: Exception) {
        log.error("mint batch queue got error for uuids $messages", e)
    }
}
