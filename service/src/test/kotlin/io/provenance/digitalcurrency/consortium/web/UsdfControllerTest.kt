package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.service.BankService
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.util.UUID

class UsdfControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @SpyBean
    lateinit var bankService: BankService

    @BeforeEach
    fun beforeEach() {
        reset(bankService)
    }

    @Nested
    inner class MintCoinTest {

        private fun <T> MintCoinRequest.execute(clazz: Class<T>): ResponseEntity<*> =
            restTemplate.exchange(
                "http://localhost:$port/dcc-test$MINT_V1",
                HttpMethod.POST,
                HttpEntity(this, defaultHeaders),
                clazz
            )

        @Test
        fun `duplicate request should return error`() {
            val uuid = UUID.randomUUID()

            transaction {
                insertRegisteredAddress(
                    uuid,
                    TEST_ADDRESS
                )
            }

            val amount = BigDecimal.TEN

            val request = MintCoinRequest(
                uuid = uuid,
                bankAccountUUID = uuid,
                amount = amount
            )

            val response = request.execute(UUID::class.java)

            assertTrue(response.statusCode.is2xxSuccessful, "Response is 200")
            assertNotNull(response.body, "Response must not be null")
            assertEquals(uuid, response.body!!, "Response is the bank account uuid")

            try {
                transaction {
                    assertNotNull(CoinMintRecord.findById(uuid))
                }
            } catch (e: Exception) {
                fail("Should not error")
            }

            val responseError = request.execute(String::class.java)

            assertTrue(responseError.statusCode.is4xxClientError, "Response is 400")
            assertNotNull(responseError.body, "Response must not be null")

            val expected = "{\"errors\":[\"IllegalStateException: Coin mint request for uuid $uuid already exists for bank account $uuid and $amount\"]}"

            assertEquals(expected, responseError.body.toString())
        }

        @Test
        fun `request for no matching bank account should return error`() {
            val uuid = UUID.randomUUID()

            val amount = BigDecimal.TEN
            val request = MintCoinRequest(
                uuid = uuid,
                bankAccountUUID = uuid,
                amount = amount
            )

            val responseError = request.execute(String::class.java)

            assertTrue(responseError.statusCode.is4xxClientError, "Response is 400")
            assertNotNull(responseError.body, "Response must not be null")

            val expected = "{\"errors\":[\"IllegalStateException: No registration found for bank account $uuid for coin mint $uuid\"]}"

            assertEquals(expected, responseError.body.toString())
        }
    }
}
