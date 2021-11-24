package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.util.UUID

class DigitalCurrencyServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var digitalCurrencyService: DigitalCurrencyService

    @Nested
    inner class RemoveAddress {
        @Test
        fun `remove address will mark a registration as deleted`() {
            val uuid = UUID.randomUUID()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.COMPLETE, txHash = randomTxHash())
            }

            digitalCurrencyService.removeAddress(uuid)

            transaction {
                val deregistration = AddressDeregistrationRecord.findByAddressRegistration(registration).firstOrNull()
                Assertions.assertNotNull(deregistration, "Deregistration exists")
                Assertions.assertNotNull(deregistration!!.addressRegistration.deleted, "Registration is deleted")
            }
        }

        @Test
        fun `missing address registration will exception`() {
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                digitalCurrencyService.removeAddress(UUID.randomUUID())
            }

            Assertions.assertTrue(exception.message!!.contains("does not exist"), "Should error with does not exist")
        }

        @Test
        fun `address registration in wrong state will exception`() {
            val uuid = UUID.randomUUID()
            transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.PENDING, txHash = randomTxHash())
            }
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                digitalCurrencyService.removeAddress(uuid)
            }

            Assertions.assertTrue(exception.message!!.contains("not in a removable status"), "Should error with not in removable status")
        }

        // Really should not be possible given status/deleted coupling but this is a sanity check
        @Test
        fun `address registration already deleted will exception`() {
            val uuid = UUID.randomUUID()
            transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.COMPLETE, txHash = randomTxHash()).apply {
                    deleted = OffsetDateTime.now()
                }
            }
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                digitalCurrencyService.removeAddress(uuid)
            }

            Assertions.assertTrue(exception.message!!.contains("is already removed"), "Should error with already removed")
        }
    }
}
