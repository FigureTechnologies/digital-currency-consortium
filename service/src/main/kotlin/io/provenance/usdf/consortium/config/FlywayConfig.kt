package io.provenance.usdf.consortium.config

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfo
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class FlywayConfig(private val dataSource: DataSource) {
    private fun MigrationInfo.statusIndication() = if (installedOn != null) "✓" else "+"
    private val log = logger()

    @Bean
    fun flyway(databaseProperties: DatabaseProperties): Flyway = Flyway
        .configure()
        .dataSource(dataSource)
        .schemas(databaseProperties.schema)
        .defaultSchema(databaseProperties.schema)
        .load()

    @Bean
    fun flywayInitializer(flyway: Flyway): FlywayMigrationInitializer = FlywayMigrationInitializer(flyway).apply {
        // flyway.clean()
        flyway.info().all().forEach {
            log.info("Flyway migration: ${it.statusIndication()} ${it.type} ${it.script} ${it.physicalLocation}")
        }
    }
}
