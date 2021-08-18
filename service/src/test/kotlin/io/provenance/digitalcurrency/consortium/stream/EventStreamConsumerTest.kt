package io.provenance.digitalcurrency.consortium.stream

import com.nhaarman.mockitokotlin2.reset
import io.provenance.digitalcurrency.consortium.TestContainer
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean

@TestContainer
class EventStreamConsumerTest(
    private val provenanceProperties: ProvenanceProperties,
    private val serviceProperties: ServiceProperties
) {
    @Autowired
    private lateinit var eventStreamProperties: EventStreamProperties

    @MockBean
    private lateinit var eventStreamFactory: EventStreamFactory

    @MockBean
    lateinit var pbcService: PbcService

    class EventStreamConsumerWrapper(
        eventStreamFactory: EventStreamFactory,
        pbcService: PbcService,
        eventStreamProperties: EventStreamProperties,
        provenanceProperties: ProvenanceProperties,
        serviceProperties: ServiceProperties
    ) : EventStreamConsumer(
        eventStreamFactory,
        pbcService,
        eventStreamProperties,
        provenanceProperties,
        serviceProperties
    ) {
        fun testHandleEvents(
            mints: Mints = listOf(),
            burns: Burns = listOf(),
            redemptions: Redemptions = listOf(),
            transfers: Transfers = listOf()
        ) {
            super.handleEvents(0L, mints, burns, redemptions, transfers)
        }
    }

    private lateinit var eventStreamConsumerWrapper: EventStreamConsumerWrapper

    @BeforeEach
    fun beforeEach() {
        reset(eventStreamFactory)
    }

    @BeforeAll
    fun beforeAll() {
        eventStreamConsumerWrapper = EventStreamConsumerWrapper(
            eventStreamFactory,
            pbcService,
            eventStreamProperties,
            provenanceProperties,
            serviceProperties
        )
    }
}
