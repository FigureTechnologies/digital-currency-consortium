package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

@MockBean(BankClient::class, PbcService::class)
@TestContainer
class BaseIntegrationTest : DatabaseTest() {
    @LocalServerPort protected var port = 0

    internal val defaultHeaders = HttpHeaders().also {
        it.accept = listOf(MediaType.APPLICATION_JSON)
        it.contentType = MediaType.APPLICATION_JSON
    }
}
