package io.provenance.digitalcurrency.report.config

import com.zaxxer.hikari.HikariConfig
import io.provenance.digitalcurrency.report.domain.ShutdownHookHikariDataSource
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
    fun dataSource(databaseProperties: DatabaseProperties): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl =
                "jdbc:postgresql://${databaseProperties.hostname}:${databaseProperties.port}/${databaseProperties.name}?prepareThreshold=0"
            username = databaseProperties.username
            password = databaseProperties.password
            schema = databaseProperties.schema
            maximumPoolSize = databaseProperties.connectionPoolSize.toInt()
        }

        return ShutdownHookHikariDataSource(emptyList(), config).also {
            Database.connect(it)
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED

            log.info(
                "Connecting database:{}:{}/{}:{}",
                databaseProperties.hostname,
                databaseProperties.port,
                databaseProperties.name,
                databaseProperties.schema
            )
        }
    }
}
