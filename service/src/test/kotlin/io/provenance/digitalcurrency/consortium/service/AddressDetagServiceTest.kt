package io.provenance.digitalcurrency.consortium.service

import io.provenance.attribute.v1.Attribute
import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.getDefaultResponse
import io.provenance.digitalcurrency.consortium.getDefaultTransactionResponse
import io.provenance.digitalcurrency.consortium.getErrorTransactionResponse
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class AddressDetagServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var bankClientProperties: BankClientProperties

    @Autowired
    lateinit var pbcService: PbcService

    @Nested
    inner class CreateEventFlow {
        @Test
        fun `inserted deregistration with tag should delete attribute`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )
            whenever(pbcService.broadcastBatch(any(), any())).thenReturn(getDefaultResponse(txHash))

            transaction {
                // TODO
                // addressDetagService.createEvent(deregistration)
            }

            verify(pbcService).broadcastBatch(any(), any())

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.PENDING,
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
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(
                pbcService.getAttributeByTagName(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName
                )
            ).thenReturn(null)

            transaction {
                // TODO
                // addressDetagService.createEvent(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.COMPLETE,
                    "Should be complete status"
                )
                Assertions.assertNull(updatedDeregistration.txHash)
            }
        }

        @Test
        fun `error in pbc call leaves in queue`() {
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(pbcService.getAttributeByTagName(TEST_ADDRESS, bankClientProperties.kycTagName)).thenReturn(
                Attribute
                    .newBuilder()
                    .setAddress(TEST_ADDRESS)
                    .setName(bankClientProperties.kycTagName)
                    .build()
            )
            // TODO put the right message here
            whenever(pbcService.broadcastBatch(any(), any())).doAnswer { throw Exception() }

            transaction {
                // TODO
                // addressDetagService.createEvent(deregistration)
            }

            // TODO put the right message here?
            verify(pbcService).broadcastBatch(any(), any())

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.QUEUED,
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
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg)
            }

            whenever(
                pbcService.getAttributeByTagName(
                    TEST_ADDRESS,
                    bankClientProperties.kycTagName
                )
            ).thenReturn(null)

            transaction {
                // TODO
                // addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.COMPLETE,
                    "Should be complete status"
                )
            }
        }

        @Test
        fun `has tag, no pbc error, should leave in queue`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg, TxStatus.PENDING, txHash)
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
                // TODO
                // addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.PENDING,
                    "Should not update tag status"
                )
            }
        }

        @Test
        fun `has tag, no pbc transaction, should reset to retry`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg, TxStatus.PENDING, txHash)
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
                // TODO
                // addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.QUEUED,
                    "Should reset tag status"
                )

                Assertions.assertNull(updatedDeregistration.txHash, "Tx hash should be null")
            }
        }

        @Test
        fun `has tag, pbc error, should reset to retry`() {
            val txHash = randomTxHash()
            val deregistration = transaction {
                val reg = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS, TxStatus.COMPLETE, randomTxHash())
                insertDeregisteredAddress(reg, TxStatus.PENDING, txHash)
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
                // TODO
                // addressDetagService.eventComplete(deregistration)
            }

            transaction {
                val updatedDeregistration = AddressDeregistrationRecord.findById(deregistration.id)!!
                Assertions.assertEquals(
                    updatedDeregistration.status,
                    TxStatus.QUEUED,
                    "Should reset tag status"
                )

                Assertions.assertNull(updatedDeregistration.txHash, "Tx hash should be null")
            }
        }
    }
}
