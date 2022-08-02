package io.provenance.digitalcurrency.consortium.config

import io.provenance.digitalcurrency.consortium.domain.HikariDataSourceBuilder
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.sql.Connection
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(value = [DatabaseProperties::class])
class DataConfig {

    private val log = logger()

    @Bean
    @Primary
    fun dataSource(databaseProperties: DatabaseProperties): DataSource =
        HikariDataSourceBuilder()
            .prefix(databaseProperties.prefix)
            .hostname(databaseProperties.hostname)
            .port(databaseProperties.port.toInt())
            .name(databaseProperties.name)
            .username(databaseProperties.username)
            .password(databaseProperties.password)
            .schema(databaseProperties.schema)
            .connectionPoolSize(databaseProperties.connectionPoolSize.toInt())
            .maxLifetimeMinutes(databaseProperties.maxLifetimeMinutes.toLong())
            .build()
            .also {
                Database.connect(it)
                TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
            }
            .also {
                log.info(
                    "Connecting database:{}:{}/{}:{}",
                    databaseProperties.hostname,
                    databaseProperties.port,
                    databaseProperties.name,
                    databaseProperties.schema
                )
            }
}
