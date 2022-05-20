package io.provenance.digitalcurrency.consortium.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.provenance.client.grpc.GasEstimationMethod.MSG_FEE_CALCULATION
import io.provenance.client.grpc.PbClient
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(
    value = [
        EventStreamProperties::class,
        ServiceProperties::class,
        ProvenanceProperties::class,
        BankClientProperties::class,
        CoroutineProperties::class,
        CoinMovementProperties::class,
        BalanceReportProperties::class,
    ]
)
class AppConfig : WebMvcConfigurer {
    @Primary
    @Bean
    fun mapper(): ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .registerModule(ProtobufModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)

    @Bean
    fun threadPoolTaskScheduler(): ThreadPoolTaskScheduler {
        val threadPoolTaskScheduler = ThreadPoolTaskScheduler()
        threadPoolTaskScheduler.poolSize = 5
        return threadPoolTaskScheduler
    }

    @Bean
    fun tendermintService(eventStreamProperties: EventStreamProperties): TendermintServiceOpenApiClient =
        TendermintServiceOpenApiClient(eventStreamProperties.rpcUri)

    @Bean
    fun pbClient(provenanceProperties: ProvenanceProperties): PbClient =
        PbClient(provenanceProperties.chainId, provenanceProperties.uri(), MSG_FEE_CALCULATION)

    @Bean
    fun bankClient(mapper: ObjectMapper, bankClientProperties: BankClientProperties): BankClient =
        BankClient.Builder(bankClientProperties.uri, bankClientProperties.context, mapper).build()
}
