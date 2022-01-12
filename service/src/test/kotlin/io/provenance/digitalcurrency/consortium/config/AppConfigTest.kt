package io.provenance.digitalcurrency.consortium.config

import com.tinder.scarlet.Scarlet
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.stream.EventStreamFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfigTest {

    @Bean
    fun eventStreamFactory(rpcClient: RpcClient, eventStreamBuilder: Scarlet.Builder) =
        EventStreamFactory(rpcClient, eventStreamBuilder)
}
