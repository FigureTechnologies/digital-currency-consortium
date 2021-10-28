package io.provenance.digitalcurrency.consortium.service

import io.provenance.attribute.v1.Attribute
import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressStatus.COMPLETE
import io.provenance.digitalcurrency.consortium.domain.AddressStatus.INSERTED
import io.provenance.digitalcurrency.consortium.domain.AddressStatus.PENDING_TAG
import io.provenance.digitalcurrency.consortium.getDefaultResponse
import io.provenance.digitalcurrency.consortium.getDefaultTransactionResponse
import io.provenance.digitalcurrency.consortium.getErrorTransactionResponse
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class AddressDetagServiceTest : BaseIntegrationTest() {

    private lateinit var addressDetagService: AddressDetagService

    @Autowired
    lateinit var bankClientProperties: BankClientProperties

    @Autowired
    lateinit var pbcService: PbcService

    @BeforeAll
    fun beforeAll() {
        addressDetagService = AddressDetagService(pbcService, bankClientProperties)
    }

    @Nested
    inner class CreateEventFlow {
        @Test
        fun `inserted deregistration with tag should delete attribute`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )
            whenever(
                pbcService.deleteAttribute(
                    TEST_ADDRESS, bankClientProperties.kycTagName
                )
            ).thenReturn(getDefaultResponse(txHash))

            transaction {
                addressDetagService.createEvent(deregistration)
            }

            verify(pbcService).deleteAttribute(
                TEST_ADDRESS,
                bankClientProperties.kycTagName
            )

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    PENDING_TAG,
                    "Should be pending tag status"
                )
                Assertions.assertEquals(
                    updatedDeregistration.txHash,
                    txHash,
                    "Hash should be updated"
                )
            }
        }

        @Test
        fun `inserted deregistration without tag just set to complete`() {
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(
                pbcService.getAttributeByTagName(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName
                )
            ).thenReturn(null)

            transaction {
                addressDetagService.createEvent(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    COMPLETE,
                    "Should be complete status"
                )
                Assertions.assertNull(updatedDeregistration.txHash)
            }
        }

        @Test
        fun `error in pbc call leaves in queue`() {
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )
            whenever(
                pbcService.deleteAttribute(
                    TEST_ADDRESS, bankClientProperties.kycTagName
                )
            ).doAnswer { throw Exception() }

            transaction {
                addressDetagService.createEvent(deregistration)
            }

            verify(pbcService).deleteAttribute(
                TEST_ADDRESS,
                bankClientProperties.kycTagName
            )

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    INSERTED,
                    "Should not update tag status"
                )
                Assertions.assertNull(updatedDeregistration.txHash, "hash should be null")
            }
        }
    }

    @Nested
    inner class CompleteEventFlow {
        @Test
        fun `detagged deregistration should complete`() {
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(
                pbcService.getAttributeByTagName(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName
                )
            ).thenReturn(null)

            transaction {
                addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    COMPLETE,
                    "Should be complete status"
                )
            }
        }

        @Test
        fun `has tag, no pbc error, should leave in queue`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg, PENDING_TAG, txHash)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )

            whenever(pbcService.getTransaction(txHash)).thenReturn(getDefaultTransactionResponse(txHash))

            transaction {
                addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    PENDING_TAG,
                    "Should not update tag status"
                )
            }
        }

        @Test
        fun `has tag, no pbc transaction, should reset to retry`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg, PENDING_TAG, txHash)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )

            whenever(pbcService.getTransaction(txHash)).thenReturn(null)

            transaction {
                addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    INSERTED,
                    "Should reset tag status"
                )

                Assertions.assertNull(updatedDeregistration.txHash, "Tx hash should be null")
            }
        }

        @Test
        fun `has tag, pbc error, should reset to retry`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg, PENDING_TAG, txHash)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )

            whenever(pbcService.getTransaction(txHash)).thenReturn(getErrorTransactionResponse(txHash))

            transaction {
                addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    INSERTED,
                    "Should reset tag status"
                )

                Assertions.assertNull(updatedDeregistration.txHash, "Tx hash should be null")
            }
        }
    }
}
