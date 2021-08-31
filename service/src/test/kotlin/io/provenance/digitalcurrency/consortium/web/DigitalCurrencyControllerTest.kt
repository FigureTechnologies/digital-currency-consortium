package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RegisterAddressRequest
import io.provenance.digitalcurrency.consortium.domain.ART
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationTable
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintTable
import io.provenance.digitalcurrency.consortium.service.DigitalCurrencyService
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.util.UUID

class DigitalCurrencyControllerTest : BaseIntegrationTest() {
    @MockBean
    lateinit var pbcService: PbcService

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @SpyBean
    lateinit var digitalCurrencyService: DigitalCurrencyService

    @BeforeEach
    fun beforeEach() {
        reset(pbcService)
        reset(digitalCurrencyService)
    }

    @AfterEach
    fun afterEach() {
        transaction {
            CoinMintTable.deleteAll()
            AddressRegistrationTable.deleteAll()
        }
    }

    @Nested
    inner class RegisterAddressTest {

        private fun <T> RegisterAddressRequest.execute(clazz: Class<T>): ResponseEntity<*> =
            restTemplate.exchange(
                "http://localhost:$port/dcc-test$REGISTRATION_V1",
                HttpMethod.POST,
                HttpEntity(this, defaultHeaders),
                clazz
            )

        @Test
        fun `valid request should save a new address registration`() {
            val uuid = UUID.randomUUID()

            val response = RegisterAddressRequest(
                bankAccountUuid = uuid,
                blockchainAddress = TEST_ADDRESS
            ).execute(UUID::class.java)

            verify(pbcService, never()).addAttribute(any(), any(), any())

            assertTrue(response.statusCode.is2xxSuccessful, "Response is 200")
            assertNotNull(response.body, "Response must not be null")
            assertEquals(uuid, response.body!!, "Response is the bank account uuid")

            try {
                transaction {
                    AddressRegistrationRecord.find {
                        ART.bankAccountUuid eq uuid
                    }.first().let {
                        assertEquals(it.address, TEST_ADDRESS)
                    }
                }
            } catch (e: Exception) {
                fail("Should not error")
            }
        }

        @Test
        fun `duplicate bank account uuid request should return error`() {
            val uuid = UUID.randomUUID()
            val request = RegisterAddressRequest(
                bankAccountUuid = uuid,
                blockchainAddress = TEST_ADDRESS
            )

            val response = request.execute(UUID::class.java)

            verify(pbcService, never()).addAttribute(any(), any(), any())

            assertTrue(response.statusCode.is2xxSuccessful, "Response is 200")
            assertNotNull(response.body, "Response must not be null")
            assertEquals(uuid, response.body!!, "Response is the bank account uuid")

            try {
                transaction {
                    AddressRegistrationRecord.find {
                        ART.bankAccountUuid eq uuid
                    }.first().let {
                        assertEquals(it.address, TEST_ADDRESS)
                    }
                }
            } catch (e: Exception) {
                fail("Should not error")
            }

            val responseError = request.execute(String::class.java)

            verify(pbcService, never()).addAttribute(any(), any(), any())

            assertTrue(responseError.statusCode.is4xxClientError, "Response is 400")
            assertNotNull(responseError.body, "Response must not be null")

            val expected = "{\"errors\":[\"IllegalStateException: Bank account $uuid is already registered for address test-address\"]}"

            assertEquals(expected, responseError.body.toString())
        }

        @Test
        fun `service method error should error`() {
            val uuid = UUID.randomUUID()
            val request = RegisterAddressRequest(
                bankAccountUuid = uuid,
                blockchainAddress = TEST_ADDRESS
            )

            whenever(digitalCurrencyService.registerAddress(uuid, TEST_ADDRESS)).doAnswer { throw Exception() }

            val responseError = request.execute(String::class.java)

            verify(pbcService, never()).addAttribute(any(), any(), any())

            assertTrue(responseError.statusCode.is5xxServerError, "Response is 500")
        }
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

            val expected = "{\"errors\":[\"IllegalArgumentException: No registration found for bank account $uuid for coin mint $uuid\"]}"

            assertEquals(expected, responseError.body.toString())
        }
    }
}
