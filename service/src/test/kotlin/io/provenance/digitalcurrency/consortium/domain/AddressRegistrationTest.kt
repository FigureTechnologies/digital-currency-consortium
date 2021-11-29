package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class AddressRegistrationTest : BaseIntegrationTest() {

    @Test
    fun `duplicate address exceptions for uniqueness constraint`() {
        transaction { insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS) }

        val exception = Assertions.assertThrows(ExposedSQLException::class.java) {
            transaction { insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS) }
        }

        Assertions.assertTrue(exception.message!!.contains("violates unique constraint"), "Message should contain violates unique constraint")
    }

    @Test
    fun `duplicate address where deleted does not trigger unique constraint`() {
        repeat(3) {
            transaction {
                val registration = insertRegisteredAddress(
                    UUID.randomUUID(), TEST_ADDRESS,
                    TxStatus.TXN_COMPLETE, randomTxHash()
                )
                registration.deleted = insertDeregisteredAddress(registration).created
            }
        }

        val uuid = transaction { insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS) }.id.value

        transaction {
            Assertions.assertEquals(4, AddressRegistrationRecord.all().count())
            Assertions.assertEquals(3, AddressRegistrationRecord.all().filter { it.deleted != null }.count())
            Assertions.assertEquals(1, AddressRegistrationRecord.all().filter { it.deleted == null }.count())
            Assertions.assertEquals(TxStatus.QUEUED, AddressRegistrationRecord.findById(uuid)!!.status, "New address registration is inserted")
        }
    }

    @Nested
    inner class FindLatestByAddress {
        @Test
        fun `always return the active one where available`() {
            repeat(3) {
                transaction {
                    val registration = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.TXN_COMPLETE, randomTxHash())
                    registration.deleted = insertDeregisteredAddress(registration).created
                }
            }

            val uuid = transaction { insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS) }.id.value

            transaction {
                Assertions.assertEquals(uuid, AddressRegistrationRecord.findLatestByAddress(TEST_ADDRESS)!!.id.value)
            }
        }

        @Test
        fun `if no active registered address, return latest one`() {
            var latestUuid: UUID? = null
            repeat(3) {
                transaction {
                    val registration = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.TXN_COMPLETE, randomTxHash())
                    if (it == 0) {
                        latestUuid = registration.id.value
                        registration.created = OffsetDateTime.now().plusMinutes(1)
                    }
                    registration.deleted = insertDeregisteredAddress(registration).created
                }
            }

            transaction {
                Assertions.assertEquals(latestUuid!!, AddressRegistrationRecord.findLatestByAddress(TEST_ADDRESS)!!.id.value)
            }
        }
    }
}
