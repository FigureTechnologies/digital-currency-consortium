package io.provenance.digitalcurrency.consortium.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.validation.annotation.Validated
import java.net.URI
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

@ConstructorBinding
@ConfigurationProperties(prefix = "database")
@Validated
class DatabaseProperties(
    val prefix: String,
    val name: String,
    val username: String,
    val password: String,
    val hostname: String,
    val port: String,
    val schema: String,
    @NotNull @Pattern(regexp = "\\d{1,2}") val connectionPoolSize: String
)

@ConstructorBinding
@ConfigurationProperties(prefix = "service")
@Validated
class ServiceProperties(
    val name: String,
    val environment: String,
    val managerKey: String,
    val dccDenom: String
) {
    fun isProd() = environment == "production"
}

@ConstructorBinding
@ConfigurationProperties(prefix = "provenance")
@Validated
class ProvenanceProperties(
    val grpcChannelUrl: String,
    val chainId: String,
    val mainNet: String,
    val contractAddress: String,
    val contractAdminAddress: String
) {
    fun uri() = URI(grpcChannelUrl)
    fun mainNet() = mainNet == "true"
}

@ConstructorBinding
@ConfigurationProperties(prefix = "event.stream")
@Validated
class EventStreamProperties(
    val id: String,
    val websocketUri: String,
    val rpcUri: String,
    val epoch: String
)

@ConstructorBinding
@ConfigurationProperties(prefix = "bank")
@Validated
class BankClientProperties(
    val uri: String,
    val kycTagName: String,
    val denom: String
)

@ConstructorBinding
@ConfigurationProperties(prefix = "coroutine")
@Validated
class CoroutineProperties(
    val numWorkers: String,
    val pollingDelayMs: String
)
