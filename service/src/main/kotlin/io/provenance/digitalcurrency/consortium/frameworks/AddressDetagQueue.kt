package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressStatus.INSERTED
import io.provenance.digitalcurrency.consortium.domain.AddressStatus.PENDING_TAG
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.AddressDetagService
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
    private val addressDetagService: AddressDetagService
) : ActorModel<AddressDetagDirective, AddressDetagOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start address detag framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<AddressDetagDirective> =
        transaction {
            AddressDeregistrationRecord.findPending().map { AddressDetagDirective(it.id.value) }
        }

    override fun processMessage(message: AddressDetagDirective): AddressDetagOutcome {
        transaction {
            AddressDeregistrationRecord.findForUpdate(message.id).first().let { addressDeregistration ->
                withMdc(*addressDeregistration.mdc()) {
                    when (addressDeregistration.status) {
                        INSERTED -> addressDetagService.createEvent(addressDeregistration)
                        PENDING_TAG -> addressDetagService.eventComplete(addressDeregistration)
                        else -> log.error("Invalid status - should never get here")
                    }
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
