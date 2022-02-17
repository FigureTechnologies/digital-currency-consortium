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
    val port: Int,
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
    val managerKeyHarden: Boolean,
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
    val mainNet: Boolean,
    val contractAddress: String,
    val gasAdjustment: Double
) {
    fun uri() = URI(grpcChannelUrl)
}

@ConstructorBinding
@ConfigurationProperties(prefix = "event.stream")
@Validated
class EventStreamProperties(
    val id: String,
    val coinMovementId: String,
    val websocketUri: String,
    val rpcUri: String,
    val epoch: Long,
    val coinMovementEpoch: Long,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "bank")
@Validated
class BankClientProperties(
    val uri: String,
    val context: String,
    val kycTagName: String,
    val denom: String
)

@ConstructorBinding
@ConfigurationProperties(prefix = "coroutine")
@Validated
class CoroutineProperties(
    val numWorkers: Int,
    val pollingDelayMs: Long,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "coin.movement")
@Validated
class CoinMovementProperties(
    val pollingDelayMs: Long,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "balance.report")
@Validated
class BalanceReportProperties(
    val pageSize: Int,
    val pollingDelayMs: Long,
    addressesWhitelist: String,
) {
    val addresses: List<String> = when (addressesWhitelist.isBlank()) {
        true -> listOf()
        false -> addressesWhitelist.split(",")
    }
}
