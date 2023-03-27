package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.config.BalanceReportProperties
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryRecord
import io.provenance.digitalcurrency.consortium.domain.BalanceReportRecord
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class BalanceReportDirective(
    override val id: UUID,
) : Directive()

class BalanceReportOutcome(
    override val id: UUID,
) : Outcome()

@Component
@NotTest
class BalanceReportQueue(
    balanceReportProperties: BalanceReportProperties,
    private val serviceProperties: ServiceProperties,
    coroutineProperties: CoroutineProperties,
    private val pbcService: PbcService,
) : ActorModel<BalanceReportDirective, BalanceReportOutcome> {

    private val log = logger()
    private val denom = serviceProperties.dccDenom
    private val whitelistAddresses = balanceReportProperties.addresses

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start balance report framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<BalanceReportDirective> =
        transaction {
            BalanceReportRecord.findPending().map { BalanceReportDirective(it.id.value) }
        }

    override fun processMessage(message: BalanceReportDirective): BalanceReportOutcome {
        transaction {
            val balanceReport = BalanceReportRecord.findForUpdate(message.id).first()
            if (balanceReport.completed != null) {
                return@transaction // already processed
            }

            // TODO - paginate addresses query
            val addresses = AddressRegistrationRecord.all().map { it.address }

            (addresses + whitelistAddresses).toSet().forEach { address ->
                BalanceEntryRecord.insert(
                    report = balanceReport,
                    address = address,
                    denom = denom,
                    // TODO - retryable?
                    amount = pbcService.getCoinBalance(address, serviceProperties.dccDenom),
                )
            }

            balanceReport.markCompleted()
        }

        return BalanceReportOutcome(message.id)
    }

    override fun onMessageSuccess(result: BalanceReportOutcome) {
        log.info("balance report queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: BalanceReportDirective, e: Exception) {
        log.error("balance report queue got error for uuid ${message.id}", e)
    }
}
