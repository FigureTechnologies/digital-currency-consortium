package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.PbcService
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
    private val pbcService: PbcService,
    private val bankClientProperties: BankClientProperties
) : ActorModel<AddressTagDirective, AddressTagOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start address tag framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<AddressTagDirective> =
        transaction {
            AddressRegistrationRecord.findPending().map { AddressTagDirective(it.id.value) }
        }

    override fun processMessage(message: AddressTagDirective): AddressTagOutcome {
        transaction {
            AddressRegistrationRecord.findForUpdate(message.id).first().let { addressRegistration ->
                withMdc(*addressRegistration.mdc()) {
                    val existing =
                        pbcService.getAttributeByTagName(
                            addressRegistration.address,
                            bankClientProperties.kycTagName
                        )
                    when (existing == null) {
                        true -> {
                            pbcService.getTransaction(addressRegistration.txHash!!)?.let { response ->
                                if (response.txResponse.isFailed()) {
                                    log.info("Tag failed - resetting record to retry")
                                    addressRegistration.resetForRetry()
                                } else {
                                    log.error("No tag but the blockchain msg succeeded. Should not happen.")
                                    addressRegistration.status = TxStatus.ERROR
                                }
                            } ?: log.info("blockchain tag not done yet - will check next iteration.")
                        }
                        false -> {
                            log.info("tag completed")
                            addressRegistration.status = TxStatus.COMPLETE
                        }
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
