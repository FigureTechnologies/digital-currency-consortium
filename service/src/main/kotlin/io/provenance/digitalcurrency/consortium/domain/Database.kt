package io.provenance.digitalcurrency.consortium.domain

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.provenance.digitalcurrency.consortium.config.logger
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.sql.DataSource

class HikariDataSourceBuilder {
    private var prefix: String = "jdbc"
    private var hostname: String? = null
    private var port: Int? = null
    private var name: String? = null
    private var schema: String? = null
    private var username: String? = null
    private var password: String? = null
    private var connectionPoolSize: Int? = null
    private var connectionTimeout: Long? = null
    private var leakDetectionThreshold: Long? = null
    private var idleTimeout: Long? = null
    private var maxLifetime: Long? = null
    private var properties: MutableMap<String, String> = mutableMapOf()
    private val shutdownHooks: MutableList<Runnable> = mutableListOf()

    fun prefix(prefix: String): HikariDataSourceBuilder {
        this.prefix = prefix
        return this
    }

    fun hostname(hostname: String): HikariDataSourceBuilder {
        this.hostname = hostname
        return this
    }

    fun port(port: Int): HikariDataSourceBuilder {
        this.port = port
        return this
    }

    fun name(name: String): HikariDataSourceBuilder {
        this.name = name
        return this
    }

    fun schema(schema: String): HikariDataSourceBuilder {
        this.schema = schema
        return this
    }

    fun username(username: String): HikariDataSourceBuilder {
        this.username = username
        return this
    }

    fun password(password: String): HikariDataSourceBuilder {
        this.password = password
        return this
    }

    fun connectionPoolSize(connectionPoolSize: Int): HikariDataSourceBuilder {
        this.connectionPoolSize = connectionPoolSize
        return this
    }

    fun connectionTimeout(connectionTimeout: Long): HikariDataSourceBuilder {
        this.connectionTimeout = connectionTimeout
        return this
    }

    fun idleTimeout(idleTimeout: Long): HikariDataSourceBuilder {
        this.idleTimeout = idleTimeout
        return this
    }

    fun leakDetectionThreshold(leakDetectionThreshold: Long): HikariDataSourceBuilder {
        this.leakDetectionThreshold = leakDetectionThreshold
        return this
    }

    fun maxLifetime(maxLifetime: Long): HikariDataSourceBuilder {
        this.maxLifetime = maxLifetime
        return this
    }

    fun build(): DataSource {
        val config = HikariConfig()

        config.jdbcUrl = "${this.prefix}:postgresql://${this.hostname}:${this.port}/${this.name}?prepareThreshold=0"
        config.username = this.username
        config.password = this.password
        if (this.schema != null) {
            config.schema = this.schema
        }
        this.properties.forEach { config.addDataSourceProperty(it.key, it.value) }
        connectionPoolSize?.run {
            val minimumIdle = this.div(2)
            config.minimumIdle = if (minimumIdle > 0) minimumIdle else 1
            config.maximumPoolSize = this
        }
        connectionTimeout?.run { config.connectionTimeout = this }
        idleTimeout?.run { config.idleTimeout = this }
        leakDetectionThreshold?.run { config.leakDetectionThreshold = this }
        maxLifetime?.run { config.maxLifetime = this }

        return ShutdownHookHikariDataSource(shutdownHooks, config)
    }
}

class ShutdownHookHikariDataSource(private val shutdownHooks: List<Runnable>, config: HikariConfig) :
    HikariDataSource(config) {
    override fun close() {
        shutdownHooks.forEach(Runnable::run)
        super.close()
    }

    override fun getConnection(): Connection {
        logger().debug("Fetching db connection from thread ${Thread.currentThread().name}")
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
