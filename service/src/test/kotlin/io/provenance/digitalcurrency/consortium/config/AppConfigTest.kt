package io.provenance.digitalcurrency.consortium.config

import io.provenance.digitalcurrency.consortium.stream.EventStreamFactory
import org.mockito.kotlin.mock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfigTest {

    @Bean
    fun eventStreamFactory(): EventStreamFactory = mock()
}
