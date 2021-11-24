package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class AddressDetagDirective(
    override val id: UUID
) : Directive()

class AddressDetagOutcome(
    override val id: UUID
) : Outcome()

@Component
class AddressDetagQueue(
    coroutineProperties: CoroutineProperties,
    private val pbcService: PbcService,
) : ActorModel<AddressDetagDirective, AddressDetagOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start address detag framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<AddressDetagDirective> =
        transaction {
            AddressDeregistrationRecord.findPending().map { AddressDetagDirective(it.id.value) }
        }

    override fun processMessage(message: AddressDetagDirective): AddressDetagOutcome {
        transaction {
            AddressDeregistrationRecord.findPendingForUpdate(message.id).first().let { addressDeregistration ->
                withMdc(*addressDeregistration.mdc()) {
                    pbcService.getTransaction(addressDeregistration.txHash!!)?.let { response ->
                        if (response.txResponse.isFailed()) {
                            log.info("Detag failed - resetting record to retry")
                            addressDeregistration.resetForRetry()
                        } else {
                            log.info("detag completed")
                            addressDeregistration.status = TxStatus.COMPLETE
                        }
                    } ?: log.info("blockchain detag not done yet - will check next iteration.")
                }
            }
        }
        return AddressDetagOutcome(message.id)
    }

    override fun onMessageSuccess(result: AddressDetagOutcome) {
        log.info("address detag queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: AddressDetagDirective, e: Exception) {
        log.error("address detag queue got error for uuid ${message.id}", e)
    }
}
