package io.provenance.digitalcurrency.consortium.service

import com.google.protobuf.ByteString
import io.provenance.attribute.v1.Attribute
import io.provenance.digitalcurrency.consortium.DatabaseTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.TestContainer
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.domain.ART
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationStatus
import io.provenance.digitalcurrency.consortium.extension.toByteArray
import io.provenance.digitalcurrency.consortium.getDefaultResponse
import io.provenance.digitalcurrency.consortium.getErrorTransactionResponse
import io.provenance.digitalcurrency.consortium.getTransactionResponse
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.UUID

@TestContainer
class AddressTagServiceTest : DatabaseTest() {

    private lateinit var addressTagService: AddressTagService

    @Autowired
    lateinit var bankClientProperties: BankClientProperties

    @MockBean
    lateinit var pbcService: PbcService

    @BeforeAll
    fun beforeAll() {
        addressTagService = AddressTagService(pbcService, bankClientProperties)
    }

    @AfterEach
    fun afterEach() {
        transaction {
            ART.deleteAll()
        }
    }

    @Nested
    inner class CreateEventFlow {
        @Test
        fun `inserted registration with no tag should add one`() {
            val uuid = UUID.randomUUID()
            val txHash = randomTxHash()
            val registration = transaction { insertRegisteredAddress(uuid, TEST_ADDRESS) }

            whenever(
                pbcService.getAttributeByTagName(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName
                )
            ).thenReturn(null)
            whenever(
                pbcService.addAttribute(
                    TEST_ADDRESS, bankClientProperties.kycTagName,
                    ByteString.copyFrom(uuid.toByteArray())
                )
            ).thenReturn(getDefaultResponse(txHash))

            transaction {
                addressTagService.createEvent(registration)
            }

            verify(pbcService).addAttribute(
                TEST_ADDRESS,
                bankClientProperties.kycTagName,
                ByteString.copyFrom(uuid.toByteArray())
            )

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.PENDING_TAG,
                    "Should be pending tag status"
                )
                assertEquals(
                    updatedRegistration.txHash,
                    txHash,
                    "Hash should be updated"
                )
            }
        }

        @Test
        fun `inserted registration with existing tag should not add one`() {
            val uuid = UUID.randomUUID()
            val registration = transaction { insertRegisteredAddress(uuid, TEST_ADDRESS) }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )

            transaction {
                addressTagService.createEvent(registration)
            }

            verify(pbcService, never()).addAttribute(
                TEST_ADDRESS,
                bankClientProperties.kycTagName,
                ByteString.copyFrom(uuid.toByteArray())
            )

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.COMPLETE,
                    "Should be completed tag status"
                )
            }
        }

        @Test
        fun `error in pbc call leaves in queue`() {
            val uuid = UUID.randomUUID()
            val registration = transaction { insertRegisteredAddress(uuid, TEST_ADDRESS) }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(null)

            whenever(
                pbcService.addAttribute(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName,
                    ByteString.copyFrom(uuid.toByteArray())
                )
            ).doAnswer { throw Exception() }

            transaction {
                addressTagService.createEvent(registration)
            }

            verify(pbcService).addAttribute(
                TEST_ADDRESS,
                bankClientProperties.kycTagName,
                ByteString.copyFrom(uuid.toByteArray())
            )

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.INSERTED,
                    "Should not update tag status"
                )
                assertNull(updatedRegistration.txHash, "hash should be null")
            }
        }
    }

    @Nested
    inner class CompleteEventFlow {
        @Test
        fun `tagged registration should complete`() {
            val uuid = UUID.randomUUID()
            val txHash = randomTxHash()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS).also {
                    it.status = AddressRegistrationStatus.PENDING_TAG
                }
            }

            whenever(
                pbcService.getAttributeByTagName(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName
                )
            ).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )

            transaction {
                addressTagService.eventComplete(registration)
            }

            verify(pbcService, never()).addAttribute(any(), any(), any())

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.COMPLETE,
                    "Should be pending tag status"
                )
            }
        }

        @Test
        fun `no tag, no pbc error, should leave in queue`() {
            val uuid = UUID.randomUUID()
            val txHash = randomTxHash()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS).also {
                    it.status = AddressRegistrationStatus.PENDING_TAG
                    it.txHash = txHash
                }
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(null)

            whenever(pbcService.getTransaction(txHash)).thenReturn(getTransactionResponse(txHash))

            transaction {
                addressTagService.eventComplete(registration)
            }

            verify(pbcService, never()).addAttribute(any(), any(), any())

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.PENDING_TAG,
                    "Should not update tag status"
                )
            }
        }

        @Test
        fun `no tag, no pbc transaction, should reset to retry`() {
            val uuid = UUID.randomUUID()
            val txHash = randomTxHash()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS).also {
                    it.status = AddressRegistrationStatus.PENDING_TAG
                    it.txHash = txHash
                }
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(null)

            whenever(pbcService.getTransaction(txHash)).thenReturn(null)

            transaction {
                addressTagService.eventComplete(registration)
            }

            verify(pbcService, never()).addAttribute(any(), any(), any())

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.INSERTED,
                    "Should reset tag status"
                )

                assertNull(updatedRegistration.txHash, "Tx hash should be null")
            }
        }

        @Test
        fun `no tag, pbc error, should reset to retry`() {
            val uuid = UUID.randomUUID()
            val txHash = randomTxHash()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS).also {
                    it.status = AddressRegistrationStatus.PENDING_TAG
                    it.txHash = txHash
                }
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(null)

            whenever(pbcService.getTransaction(txHash)).thenReturn(getErrorTransactionResponse(txHash))

            transaction {
                addressTagService.eventComplete(registration)
            }

            verify(pbcService, never()).addAttribute(any(), any(), any())

            transaction {
                val updatedRegistration = AddressRegistrationRecord.findByAddress(TEST_ADDRESS)!!
                assertEquals(
                    updatedRegistration.status,
                    AddressRegistrationStatus.INSERTED,
                    "Should reset tag status"
                )

                assertNull(updatedRegistration.txHash, "Tx hash should be null")
            }
        }
    }
}
