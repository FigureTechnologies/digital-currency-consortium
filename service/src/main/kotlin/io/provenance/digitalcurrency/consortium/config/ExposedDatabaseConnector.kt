package io.provenance.digitalcurrency.consortium.config

import io.provenance.digitalcurrency.consortium.frameworks.DataSourceConnectedEvent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

@Component
class ExposedDatabaseConnector(
    private val dataSource: DataSource,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val log = logger()

    @EventListener(ApplicationReadyEvent::class)
    fun configureDataSource() {
        log.info("configuring data source")
        Database.connect(dataSource)
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED

        applicationEventPublisher.publishEvent(DataSourceConnectedEvent(this, "data source connected"))
    }
}
