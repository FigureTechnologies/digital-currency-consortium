package io.provenance.digitalcurrency.consortium.config

import com.nhaarman.mockitokotlin2.mock
import io.provenance.digitalcurrency.consortium.stream.EventStreamFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfigTest {

    @Bean
    fun eventStreamFactory(): EventStreamFactory = mock()
}
