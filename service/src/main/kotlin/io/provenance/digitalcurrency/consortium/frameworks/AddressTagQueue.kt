package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationStatus
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.AddressTagService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class AddressTagDirective(
    override val id: UUID
) : Directive()

class AddressTagOutcome(
    override val id: UUID
) : Outcome()

@Component
class AddressTagQueue(
    coroutineProperties: CoroutineProperties,
    private val addressTagService: AddressTagService
) :
    ActorModel<AddressTagDirective, AddressTagOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start address tag framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<AddressTagDirective> =
        transaction {
            AddressRegistrationRecord.findPending().map { AddressTagDirective(it.id.value) }
        }

    override fun processMessage(message: AddressTagDirective): AddressTagOutcome {
        transaction {
            AddressRegistrationRecord.findForUpdate(message.id).first().let { addressRegistration ->
                withMdc(*addressRegistration.mdc()) {
                    when (addressRegistration.status) {
                        AddressRegistrationStatus.INSERTED -> addressTagService.createEvent(addressRegistration)
                        AddressRegistrationStatus.PENDING_TAG -> addressTagService.eventComplete(addressRegistration)
                        else -> log.error("Invalid status - should never get here")
                    }
                }
            }
        }
        return AddressTagOutcome(message.id)
    }

    override fun onMessageSuccess(result: AddressTagOutcome) {
        log.info("address tag queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: AddressTagDirective, e: Exception) {
        log.error("address tag queue got error for uuid ${message.id}", e)
    }
}
