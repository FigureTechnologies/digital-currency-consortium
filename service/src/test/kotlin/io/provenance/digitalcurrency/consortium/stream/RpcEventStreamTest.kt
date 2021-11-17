package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.TestContainer
import io.provenance.digitalcurrency.consortium.config.logger
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@TestContainer
internal class RpcEventStreamTest {
    @Autowired
    private lateinit var eventStreamFactory: EventStreamFactory
    private lateinit var rpcEventStream: RpcEventStream

    @BeforeAll
    fun beforeAll() {
        rpcEventStream = eventStreamFactory.getStream(
            eventTypes = listOf(),
            startHeight = 0,
            observer = EventStreamResponseObserver { logger().info("test") }
        )
    }

    @Test
    fun streamEvents() {
        rpcEventStream.streamEvents()
    }
}
