package io.provenance.digitalcurrency.report.domain

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.provenance.digitalcurrency.report.config.logger
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneId

// TODO - move to shared?
class ShutdownHookHikariDataSource(private val shutdownHooks: List<Runnable>, config: HikariConfig) : HikariDataSource(config) {

    private val log = logger()

    override fun close() {
        shutdownHooks.forEach(Runnable::run)
        super.close()
    }

    override fun getConnection(): Connection {
        log.debug("Fetching db connection from thread ${Thread.currentThread().name}")
        return super.getConnection()
    }
}

fun <T : Table> T.offsetDatetime(name: String): Column<OffsetDateTime> =
    registerColumn(name, OffsetDateTimeColumnType())

class OffsetDateTimeColumnType : ColumnType() {
    override fun sqlType(): String = "TIMESTAMPTZ"

    override fun valueFromDB(value: Any): Any = when (value) {
        is java.sql.Date -> OffsetDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())
        is java.sql.Timestamp -> OffsetDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())
        else -> value
    }
}
