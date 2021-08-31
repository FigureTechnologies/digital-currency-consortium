package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class DigitalCurrencyService(
    private val bankClientProperties: BankClientProperties
) {
    private val log = logger()

    fun registerAddress(bankAccountUuid: UUID, blockchainAddress: String) {
        log.info("Registering bank account $bankAccountUuid for address $blockchainAddress with tag ${bankClientProperties.kycTagName}")
        transaction {
            check(AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid) == null) {
                "Bank account $bankAccountUuid is already registered for address $blockchainAddress"
            }

            val existingByAddress = AddressRegistrationRecord.findByAddress(blockchainAddress)
            check(existingByAddress == null) {
                "Address $blockchainAddress is already registered for bank account uuid ${existingByAddress!!.bankAccountUuid}"
            }

            AddressRegistrationRecord.insert(
                bankAccountUuid = bankAccountUuid,
                address = blockchainAddress
            )
        }
    }

    fun mintCoin(uuid: UUID, bankAccountUuid: UUID, amount: BigDecimal) =
        transaction {
            log.info("Minting coin for $uuid to bank account $bankAccountUuid for amount $amount")
            check(CoinMintRecord.findById(uuid) == null) {
                "Coin mint request for uuid $uuid already exists for bank account $bankAccountUuid and $amount"
            }

            val registration = AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid)
            require(registration != null) { "No registration found for bank account $bankAccountUuid for coin mint $uuid" }

            CoinMintRecord.insert(
                uuid = uuid,
                addressRegistration = registration,
                fiatAmount = amount
            )
        }
}
