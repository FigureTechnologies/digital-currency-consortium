package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedeemBurnRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class BankServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var bankService: BankService

    @Nested
    inner class RemoveAddress {
        @Test
        fun `remove address will mark a registration as deleted`() {
            val uuid = UUID.randomUUID()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.TXN_COMPLETE, txHash = randomTxHash())
            }

            bankService.removeAddress(uuid)

            transaction {
                val deregistration = AddressDeregistrationRecord.findByAddressRegistration(registration).firstOrNull()
                Assertions.assertNotNull(deregistration, "Deregistration exists")
                Assertions.assertNotNull(deregistration!!.addressRegistration.deleted, "Registration is deleted")
            }
        }

        @Test
        fun `missing address registration will exception`() {
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.removeAddress(UUID.randomUUID())
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
                bankService.removeAddress(uuid)
            }

            Assertions.assertTrue(exception.message!!.contains("not in a removable status"), "Should error with not in removable status")
        }

        // Really should not be possible given status/deleted coupling but this is a sanity check
        @Test
        fun `address registration already deleted will exception`() {
            val uuid = UUID.randomUUID()
            transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.TXN_COMPLETE, txHash = randomTxHash()).apply {
                    deleted = OffsetDateTime.now()
                }
            }
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.removeAddress(uuid)
            }

            Assertions.assertTrue(exception.message!!.contains("is already removed"), "Should error with already removed")
        }
    }

    @Nested
    inner class MintCoin {
        @Test
        fun `minting coin with accurate registration stores a coin mint record`() {
            val uuid = UUID.randomUUID()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.TXN_COMPLETE, txHash = randomTxHash())
            }

            bankService.mintCoin(uuid, registration.bankAccountUuid, BigDecimal.ONE)

            transaction {
                val coinMint = CoinMintRecord.findById(uuid)
                Assertions.assertNotNull(coinMint, "Coin mint exists")
                Assertions.assertEquals(registration.id.value, coinMint!!.addressRegistration.id.value, "Registration is the same")
            }
        }

        @Test
        fun `minting coin with invalid registration will exception`() {
            val uuid = UUID.randomUUID()

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.mintCoin(uuid, UUID.randomUUID(), BigDecimal.ONE)
            }

            Assertions.assertTrue(exception.message!!.contains("No registration found for bank account"), "Should error with no registration")
        }

        @Test
        fun `minting coin with existing uuid will exception`() {
            val (uuid, bankAccountUuid) = transaction {
                insertCoinMint(UUID.randomUUID(), TEST_ADDRESS).let { it.id.value to it.addressRegistration.id.value }
            }

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.mintCoin(uuid, bankAccountUuid, BigDecimal.ONE)
            }

            Assertions.assertTrue(exception.message!!.contains("already exists for bank account"), "Should error with already exists")
        }
    }

    @Nested
    inner class RedeemBurnCoin {

        @Autowired
        lateinit var pbcServiceMock: PbcService

        @Autowired
        private lateinit var bankClientProperties: BankClientProperties

        @Autowired
        private lateinit var serviceProperties: ServiceProperties

        private lateinit var bankService: BankService

        @BeforeEach
        fun before() {
            reset(pbcServiceMock)

            whenever(pbcServiceMock.managerAddress).thenReturn(TEST_ADDRESS)
            whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("50000") // $500
            whenever(pbcServiceMock.getMarkerEscrowBalance()).thenReturn("50000") // $500

            bankService = BankService(bankClientProperties, pbcServiceMock, serviceProperties)

            transaction {
                CoinRedeemBurnRecord.insert(UUID.randomUUID(), BigDecimal("100"))
                CoinRedeemBurnRecord.insert(UUID.randomUUID(), BigDecimal("250"))
            }
        }

        @Test
        fun `redeem burning coin with sufficient balance stores a redeem burn record`() {
            val uuid = UUID.randomUUID()

            bankService.redeemBurnCoin(uuid, BigDecimal("150"))

            transaction {
                val coinRedeemBurn = CoinRedeemBurnRecord.findById(uuid)
                Assertions.assertNotNull(coinRedeemBurn, "Coin redeem burn exists")
                Assertions.assertTrue(BigDecimal("150").compareTo(coinRedeemBurn!!.fiatAmount) == 0, "Amount is correct")
                Assertions.assertEquals(TxStatus.QUEUED, coinRedeemBurn.status, "Tx request is queued")
            }
        }

        @Test
        fun `redeem burning coin with duplicate uuid will exception`() {
            val uuid = transaction { CoinRedeemBurnRecord.all().first().id.value }

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.redeemBurnCoin(uuid, BigDecimal("1"))
            }

            Assertions.assertTrue(exception.message!!.contains("already exists"), "Should error with already exists")
        }

        @Test
        fun `redeem burning coin with insufficient dcc coin will exception`() {
            val uuid = UUID.randomUUID()

            whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("49999") // $499.99

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.redeemBurnCoin(uuid, BigDecimal("150"))
            }

            Assertions.assertTrue(exception.message!!.contains("Insufficient dcc coin"), "Should error with insufficient dcc")
        }

        @Test
        fun `redeem burning coin with insufficient reserve escrowed coin will exception`() {
            val uuid = UUID.randomUUID()

            whenever(pbcServiceMock.getMarkerEscrowBalance()).thenReturn("49999") // 499.99

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.redeemBurnCoin(uuid, BigDecimal("150"))
            }

            Assertions.assertTrue(exception.message!!.contains("Insufficient bank reserve coin escrowed"), "Should error with insufficient reserve")
        }
    }
}
