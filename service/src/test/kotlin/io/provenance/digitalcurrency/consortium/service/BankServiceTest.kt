package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_OTHER_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedeemBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus.TXN_COMPLETE
import io.provenance.digitalcurrency.consortium.messages.MemberListResponse
import io.provenance.digitalcurrency.consortium.messages.MemberResponse
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

            whenever(pbcServiceMock.managerAddress).thenReturn(TEST_MEMBER_ADDRESS)

            bankService = BankService(bankClientProperties, pbcServiceMock, serviceProperties)
        }

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
                Assertions.assertEquals(TEST_ADDRESS, registration.address, "Coin mint address is registered address")
                Assertions.assertEquals(registration.id.value, coinMint!!.addressRegistration!!.id.value, "Registration is the same")
            }
        }

        @Test
        fun `minting coin with no registration stores a coin mint record for member bank address`() {
            val uuid = UUID.randomUUID()
            bankService.mintCoin(uuid, null, BigDecimal.ONE)

            transaction {
                val coinMint = CoinMintRecord.findById(uuid)
                Assertions.assertNotNull(coinMint, "Coin mint exists")
                Assertions.assertNull(coinMint!!.addressRegistration, "No address registration")
                Assertions.assertEquals(TEST_MEMBER_ADDRESS, coinMint.address, "Coin mint address is the member address")
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
        fun `minting coin with deleted registration will exception`() {
            val uuid = UUID.randomUUID()
            val registration = transaction {
                insertRegisteredAddress(uuid, TEST_ADDRESS, status = TxStatus.TXN_COMPLETE, txHash = randomTxHash())
                    .also { it.deleted = insertDeregisteredAddress(it).created }
            }

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.mintCoin(uuid, registration.bankAccountUuid, BigDecimal.ONE)
            }

            Assertions.assertTrue(exception.message!!.contains("Cannot mint to removed bank account"), "Should error with deleted registration")
        }

        @Test
        fun `minting coin with existing uuid will exception`() {
            val (uuid, bankAccountUuid) = transaction {
                insertCoinMint(UUID.randomUUID(), TEST_ADDRESS).let { it.id.value to it.addressRegistration!!.id.value }
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

            whenever(pbcServiceMock.managerAddress).thenReturn(TEST_MEMBER_ADDRESS)
            whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("50000") // $500
            whenever(pbcServiceMock.getMarkerEscrowBalance()).thenReturn("25000") // $250

            bankService = BankService(bankClientProperties, pbcServiceMock, serviceProperties)

            transaction {
                CoinRedeemBurnRecord.insert(UUID.randomUUID(), BigDecimal("100"))
                CoinTransferRecord.insert(UUID.randomUUID(), TEST_ADDRESS, BigDecimal("250"))
            }
        }

        @Test
        fun `redeem burning coin with sufficient balance stores a redeem burn record`() {
            val uuid = UUID.randomUUID()

            // $500 usdf balance - $100 existing - $250 transfer = $150 available
            // $250 escrow balance - $100 existing = $150 available
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

            // $499.99 usdf balance - $100 existing - $250 transfer = $149.99 available
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.redeemBurnCoin(uuid, BigDecimal("150"))
            }

            Assertions.assertTrue(exception.message!!.contains("Insufficient dcc coin"), "Should error with insufficient dcc")

            // works fine with adjusted amount
            bankService.redeemBurnCoin(uuid, BigDecimal("149.99"))
        }

        @Test
        fun `redeem burning coin with insufficient reserve escrowed coin will exception`() {
            val uuid = UUID.randomUUID()

            // $500 usdf balance - $100 existing - $250 transfer = $150 available
            // $249.99 escrow balance - $100 existing = $149.99 available
            whenever(pbcServiceMock.getMarkerEscrowBalance()).thenReturn("24999") // $249.99

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.redeemBurnCoin(uuid, BigDecimal("150"))
            }

            Assertions.assertTrue(exception.message!!.contains("Insufficient bank reserve coin escrowed"), "Should error with insufficient reserve")

            // works fine with adjusted amount
            bankService.redeemBurnCoin(uuid, BigDecimal("149.99"))
        }
    }

    @Nested
    inner class TransferCoin {

        @Autowired
        lateinit var pbcServiceMock: PbcService

        @Autowired
        private lateinit var bankClientProperties: BankClientProperties

        @Autowired
        private lateinit var serviceProperties: ServiceProperties

        private lateinit var bankService: BankService

        private val bankAccountUuid = UUID.randomUUID()

        @BeforeEach
        fun before() {
            reset(pbcServiceMock)

            whenever(pbcServiceMock.managerAddress).thenReturn(TEST_MEMBER_ADDRESS)
            whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("50000") // $500
            whenever(pbcServiceMock.getMembers()).thenReturn(
                MemberListResponse(
                    members = listOf(
                        MemberResponse(
                            id = TEST_OTHER_MEMBER_ADDRESS,
                            supply = 10000,
                            maxSupply = 10000000,
                            denom = "otherbank.omni.dcc",
                            joined = 12345,
                            weight = 10000000,
                            name = "Other Bank"
                        )
                    )
                )
            )

            bankService = BankService(bankClientProperties, pbcServiceMock, serviceProperties)

            transaction {
                insertRegisteredAddress(bankAccountUuid, TEST_ADDRESS, TXN_COMPLETE)
                CoinRedeemBurnRecord.insert(UUID.randomUUID(), BigDecimal("100"))
                CoinRedeemBurnRecord.insert(UUID.randomUUID(), BigDecimal("50"))
                CoinTransferRecord.insert(UUID.randomUUID(), TEST_ADDRESS, BigDecimal("50"))
            }
        }

        @Test
        fun `transferring coin to a registered blockchain address stores a coin transfer record`() {
            val uuid = UUID.randomUUID()

            // $500 available - $100 burn - $50 burn - $50 tranfser = $300 available
            bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = TEST_ADDRESS, BigDecimal("300"))

            transaction {
                val coinTransferRecord = CoinTransferRecord.findById(uuid)
                Assertions.assertNotNull(coinTransferRecord, "Coin transfer exists")
                Assertions.assertTrue(BigDecimal("300").compareTo(coinTransferRecord!!.fiatAmount) == 0, "Amount is correct")
                Assertions.assertEquals(TxStatus.QUEUED, coinTransferRecord.status, "Tx request is queued")
                Assertions.assertEquals(TEST_ADDRESS, coinTransferRecord.address, "To address is correct")
            }
        }

        @Test
        fun `transferring coin to a registered bank account stores a coin transfer record`() {
            val uuid = UUID.randomUUID()

            // $500 available - $100 burn - $50 burn - $50 tranfser = $300 available
            bankService.transferCoin(uuid, bankAccountUuid = bankAccountUuid, blockchainAddress = null, BigDecimal("300"))

            transaction {
                val coinTransferRecord = CoinTransferRecord.findById(uuid)
                Assertions.assertNotNull(coinTransferRecord, "Coin transfer exists")
                Assertions.assertTrue(BigDecimal("300").compareTo(coinTransferRecord!!.fiatAmount) == 0, "Amount is correct")
                Assertions.assertEquals(TxStatus.QUEUED, coinTransferRecord.status, "Tx request is queued")
                Assertions.assertEquals(TEST_ADDRESS, coinTransferRecord.address, "To address is correct")
            }
        }

        @Test
        fun `transferring coin with duplicate uuid will exception`() {
            val uuid = transaction { CoinTransferRecord.all().first().id.value }

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = TEST_ADDRESS, BigDecimal("1"))
            }

            Assertions.assertTrue(exception.message!!.contains("already exists"), "Should error with already exists")
        }

        @Test
        fun `transferring coin with insufficient dcc coin will exception`() {
            val uuid = UUID.randomUUID()

            whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("49999") // $499.99

            // $499.99 available - $100 burn - $50 burn - $50 tranfser = $299.99 available
            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = TEST_ADDRESS, BigDecimal("300"))
            }

            Assertions.assertTrue(exception.message!!.contains("Insufficient dcc coin"), "Should error with insufficient dcc")

            // works fine with adjusted amount
            bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = TEST_ADDRESS, BigDecimal("299.99"))
        }

        @Test
        fun `transferring coin with deleted bank account registration will exception`() {
            val uuid = UUID.randomUUID()

            transaction {
                AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid)!!.also {
                    it.deleted = AddressDeregistrationRecord.insert(it).created
                }
            }

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.transferCoin(uuid, bankAccountUuid = bankAccountUuid, blockchainAddress = null, BigDecimal("300"))
            }

            Assertions.assertTrue(exception.message!!.contains("Cannot transfer to removed bank account"), "Should error with deleted")
        }

        @Test
        fun `transferring coin with deleted blockchain address will exception`() {
            val uuid = UUID.randomUUID()

            transaction {
                AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid)!!.also {
                    it.deleted = AddressDeregistrationRecord.insert(it).created
                }
            }

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = TEST_ADDRESS, BigDecimal("300"))
            }

            Assertions.assertTrue(exception.message!!.contains("Cannot transfer to removed bank account"), "Should error with deleted")
        }

        @Test
        fun `transferring coin with no address input will exception`() {
            val uuid = UUID.randomUUID()

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = null, BigDecimal("300"))
            }

            Assertions.assertEquals("Blockchain address cannot be null when bank account uuid is not set", exception.message, "Should error with not set")
        }

        @Test
        fun `transferring coin to a another member bank address stores a coin transfer record`() {
            val uuid = UUID.randomUUID()

            // $500 available - $100 burn - $50 burn - $50 tranfser = $300 available
            bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = TEST_OTHER_MEMBER_ADDRESS, BigDecimal("300"))

            transaction {
                val coinTransferRecord = CoinTransferRecord.findById(uuid)
                Assertions.assertNotNull(coinTransferRecord, "Coin transfer exists")
                Assertions.assertTrue(BigDecimal("300").compareTo(coinTransferRecord!!.fiatAmount) == 0, "Amount is correct")
                Assertions.assertEquals(TxStatus.QUEUED, coinTransferRecord.status, "Tx request is queued")
                Assertions.assertEquals(TEST_OTHER_MEMBER_ADDRESS, coinTransferRecord.address, "To address is correct")
            }
        }

        @Test
        fun `transferring coin with invalid address will exception`() {
            val uuid = UUID.randomUUID()

            val exception = Assertions.assertThrows(IllegalStateException::class.java) {
                bankService.transferCoin(uuid, bankAccountUuid = null, blockchainAddress = "invalid-address", BigDecimal("300"))
            }

            Assertions.assertTrue(exception.message!!.contains("No valid address found for transfer"), "Should error with no valid address")
        }
    }
}
