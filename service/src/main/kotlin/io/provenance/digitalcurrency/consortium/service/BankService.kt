package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedeemBurnRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class BankService(
    private val bankClientProperties: BankClientProperties,
    private val pbcService: PbcService,
    private val serviceProperties: ServiceProperties
) {
    private val log = logger()

    fun registerAddress(bankAccountUuid: UUID, blockchainAddress: String) {
        log.info("Registering bank account $bankAccountUuid for address $blockchainAddress with tag ${bankClientProperties.kycTagName}")
        transaction {
            check(AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid) == null) {
                "Bank account $bankAccountUuid is already registered for address $blockchainAddress"
            }

            val existingByAddress = AddressRegistrationRecord.findActiveByAddress(blockchainAddress)
            check(existingByAddress == null) {
                "Address $blockchainAddress is already registered for bank account uuid ${existingByAddress!!.bankAccountUuid}"
            }

            AddressRegistrationRecord.insert(
                bankAccountUuid = bankAccountUuid,
                address = blockchainAddress
            )
        }
    }

    fun removeAddress(bankAccountUuid: UUID) {
        log.info("Removing bank account $bankAccountUuid with tag ${bankClientProperties.kycTagName}")
        transaction {
            val existing = AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid)
            checkNotNull(existing) { "Bank account $bankAccountUuid does not exist" }
            check(existing.status == TxStatus.TXN_COMPLETE) { "Bank account $bankAccountUuid is not in a removable status ${existing.status}" }
            check(existing.deleted == null) { "Bank account $bankAccountUuid is already removed" }

            AddressDeregistrationRecord.insert(existing).apply { existing.deleted = created }
        }
    }

    fun mintCoin(uuid: UUID, bankAccountUuid: UUID, amount: BigDecimal) =
        transaction {
            log.info("Minting coin for $uuid to bank account $bankAccountUuid for amount $amount")
            check(CoinMintRecord.findById(uuid) == null) {
                "Coin mint request for uuid $uuid already exists for bank account $bankAccountUuid and $amount"
            }

            val registration = AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid)
            checkNotNull(registration) { "No registration found for bank account $bankAccountUuid for coin mint $uuid" }
            check(registration.isActive()) { "Registration for bank account $bankAccountUuid is not active" }

            CoinMintRecord.insert(
                uuid = uuid,
                addressRegistration = registration,
                fiatAmount = amount
            )
        }

    fun redeemBurnCoin(uuid: UUID, amount: BigDecimal) =
        synchronized(CoinRedeemBurnRecord::class.java) {
            transaction {
                log.info("Redeem burning coin for $uuid for amount $amount")
                check(CoinRedeemBurnRecord.findById(uuid) == null) { "Coin redeem burn request for uuid $uuid already exists" }

                val coinAmount = amount.toCoinAmount()
                // Account for any pending records in progress by netting out from balance lookups
                val pendingAmount = CoinRedeemBurnRecord.findPending()
                    .fold(0L) { acc, record -> acc + record.coinAmount }
                    .toBigInteger()

                val dccBalance = pbcService.getCoinBalance(pbcService.managerAddress, serviceProperties.dccDenom)
                    .toBigInteger() - pendingAmount
                check(coinAmount <= dccBalance) { "Insufficient dcc coin $dccBalance" }

                val markerEscrowBalance = pbcService.getMarkerEscrowBalance().toBigInteger() - pendingAmount
                check(coinAmount <= markerEscrowBalance) { "Insufficient bank reserve coin escrowed $markerEscrowBalance" }

                CoinRedeemBurnRecord.insert(uuid, amount)
            }
        }
}
