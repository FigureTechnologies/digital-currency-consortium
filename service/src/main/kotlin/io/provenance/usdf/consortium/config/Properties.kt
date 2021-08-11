package io.provenance.usdf.consortium.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

@ConfigurationProperties(prefix = "database")
@Validated
class DatabaseProperties {
    @NotNull lateinit var prefix: String
    @NotNull lateinit var name: String
    @NotNull lateinit var username: String
    @NotNull lateinit var password: String
    @NotNull lateinit var hostname: String
    @NotNull lateinit var port: String
    @NotNull lateinit var schema: String
    @NotNull @Pattern(regexp = "\\d{1,2}") lateinit var connectionPoolSize: String
}

@ConfigurationProperties(prefix = "service")
@Validated
class ServiceProperties {
    @NotNull lateinit var name: String
    @NotNull lateinit var environment: String
    @NotNull lateinit var managerKey: String

    fun isProd() = environment == "production"
}

@ConfigurationProperties(prefix = "provenance")
@Validated
class ProvenanceProperties {
    @NotNull lateinit var grpcChannelUrl: String
    @NotNull lateinit var chainId: String
    @NotNull lateinit var mainNet: String

    fun uri() = URI(grpcChannelUrl)
    fun mainNet() = mainNet == "true"
}

@ConfigurationProperties(prefix = "event.stream")
@Validated
class EventStreamProperties {
    @NotNull lateinit var id: String
    @NotNull lateinit var websocketUri: String
    @NotNull lateinit var rpcUri: String
    @NotNull lateinit var epoch: String
}
