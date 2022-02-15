package io.provenance.digitalcurrency.consortium.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.streamadapter.rxjava2.RxJava2StreamAdapterFactory
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import io.provenance.client.grpc.PbClient
import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.stream.EventStreamFactory
import okhttp3.OkHttpClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.net.URI
import java.util.concurrent.TimeUnit

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
    @NotTest
    fun eventStreamFactory(rpcClient: RpcClient, eventStreamBuilder: Scarlet.Builder) =
        EventStreamFactory(rpcClient, eventStreamBuilder)

    @Bean
    fun eventStreamBuilder(eventStreamProperties: EventStreamProperties): Scarlet.Builder {
        val node = URI(eventStreamProperties.websocketUri)
        return Scarlet.Builder()
            .webSocketFactory(
                OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS) // ~ 12 pbc block cuts
                    .build()
                    .newWebSocketFactory("${node.scheme}://${node.host}:${node.port}/websocket")
            )
            .addMessageAdapterFactory(MoshiMessageAdapter.Factory())
            .addStreamAdapterFactory(RxJava2StreamAdapterFactory())
    }

    @Bean
    fun rpcClient(mapper: ObjectMapper, eventStreamProperties: EventStreamProperties): RpcClient =
        RpcClient.Builder(eventStreamProperties.rpcUri, mapper).build()

    @Bean
    fun pbClient(provenanceProperties: ProvenanceProperties): PbClient =
        PbClient(provenanceProperties.chainId, provenanceProperties.uri())

    @Bean
    fun bankClient(mapper: ObjectMapper, bankClientProperties: BankClientProperties): BankClient =
        BankClient.Builder(bankClientProperties.uri, bankClientProperties.context, mapper).build()
}
