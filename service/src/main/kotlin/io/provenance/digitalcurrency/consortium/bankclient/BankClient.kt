package io.provenance.digitalcurrency.consortium.bankclient

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.Headers
import feign.Param
import feign.RequestLine
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.provenance.digitalcurrency.consortium.api.AlertRequest
import io.provenance.digitalcurrency.consortium.api.BalanceRequest
import io.provenance.digitalcurrency.consortium.api.CoinMovementRequest
import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import org.springframework.http.ResponseEntity
import java.util.UUID

@Headers("Content-Type: application/json")
interface BankClient {

    class EmptyRequest

    @RequestLine("POST /api/v1/mints/complete/{uuid}")
    fun completeMint(@Param("uuid") uuid: UUID, request: EmptyRequest = EmptyRequest()): ResponseEntity<String>

    @RequestLine("POST /api/v1/burns/complete/{uuid}")
    fun completeBurn(@Param("uuid") uuid: UUID, request: EmptyRequest = EmptyRequest()): ResponseEntity<String>

    @RequestLine("POST /api/v1/fiat/deposits")
    fun depositFiat(request: DepositFiatRequest): ResponseEntity<String>

    @RequestLine("POST /api/v1/transactions/logship")
    fun persistCoinMovement(request: CoinMovementRequest): ResponseEntity<String>

    @RequestLine("POST /api/v1/transactions/balreport")
    fun persistBalanceReport(request: BalanceRequest): ResponseEntity<String>

    @RequestLine("POST /api/v1/alerts")
    fun persistAlert(request: AlertRequest): ResponseEntity<String>

    class Builder(
        private val url: String,
        private val context: String,
        private val objectMapper: ObjectMapper
    ) {
        fun build(): BankClient = Feign.builder()
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .target(BankClient::class.java, "$url$context")
    }
}
