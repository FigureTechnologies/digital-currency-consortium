package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.service.PbcService
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockBean(PbcService::class)
class BaseIntegrationTest : DatabaseTest() {
    @LocalServerPort protected var port = 0

    internal val defaultHeaders = HttpHeaders().also {
        it.accept = listOf(MediaType.APPLICATION_JSON)
        it.contentType = MediaType.APPLICATION_JSON
    }
}
