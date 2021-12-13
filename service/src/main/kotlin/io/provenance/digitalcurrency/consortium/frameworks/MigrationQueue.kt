package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.MigrationRecord
import io.provenance.digitalcurrency.consortium.domain.toAlertRequest
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class MigrationDirective(
    override val id: UUID
) : Directive()

class MigrationOutcome(
    override val id: UUID
) : Outcome()

@Component
@NotTest
class MigrationQueue(
    coroutineProperties: CoroutineProperties,
    private val bankClient: BankClient
) : ActorModel<MigrationDirective, MigrationOutcome> {

    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start migration alerting framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<MigrationDirective> =
        transaction {
            MigrationRecord.findPending().map { MigrationDirective(it.id.value) }
        }

    override fun processMessage(message: MigrationDirective): MigrationOutcome {
        val migration = transaction { MigrationRecord.findForUpdate(message.id).first() }

        if (migration.sent == null) {
            bankClient.persistAlert(migration.toAlertRequest())
            transaction {
                migration.markSent()
            }
        }

        return MigrationOutcome(message.id)
    }

    override fun onMessageSuccess(result: MigrationOutcome) {
        log.info("migration alert queue successfully processed uuid ${result.id}.")
    }

    override fun onMessageFailure(message: MigrationDirective, e: Exception) {
        log.error("migration alert queue got error for uuid ${message.id}", e)
    }
}
