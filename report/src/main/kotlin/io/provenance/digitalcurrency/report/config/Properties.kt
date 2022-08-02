package io.provenance.digitalcurrency.report.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.validation.annotation.Validated

@ConstructorBinding
@ConfigurationProperties(prefix = "database")
@Validated
class DatabaseProperties(
    val prefix: String,
    val name: String,
    val username: String,
    val password: String,
    val hostname: String,
    val port: Int,
    val schema: String,
    val connectionPoolSize: Int,
    val connectionTimeout: Long,
    val leakDetectionThreshold: Long,
    val idleTimeout: Long,
    val maxLifetime: Long,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "service")
@Validated
class ServiceProperties(
    val name: String,
    val environment: String,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "event-stream")
@Validated
class EventStreamProperties(
    val id: String,
    val uri: String,
    val fromHeight: Long,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "contract")
@Validated
class ContractProperties(
    val address: String,
)
