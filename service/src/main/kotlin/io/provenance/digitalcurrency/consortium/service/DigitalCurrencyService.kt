package io.provenance.digitalcurrency.consortium.service

import com.google.protobuf.ByteString
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.extension.toByteArray
import io.provenance.digitalcurrency.consortium.util.retry
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class DigitalCurrencyService(
    private val pbcService: PbcService,
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
                "Address $blockchainAddress is already registerd for bank account uuid ${existingByAddress!!.bankAccountUuid}"
            }

            AddressRegistrationRecord.insert(
                bankAccountUuid = bankAccountUuid,
                address = blockchainAddress
            )
        }
    }

    fun tryKycTag(bankAccountUuid: UUID, blockchainAddress: String) {
        pbcService.getAttributeByTagName(blockchainAddress, bankClientProperties.kycTagName).forEach {
            // just remove if existing and replace. This really should not happen and if it does there should be only one
            log.warn("Deleting existing ${bankClientProperties.kycTagName} for address $blockchainAddress")
            pbcService.deleteAttribute(blockchainAddress, it.name)
        }

        retry {
            pbcService.addAttribute(
                address = blockchainAddress,
                tag = bankClientProperties.kycTagName,
                payload = ByteString.copyFrom(bankAccountUuid.toByteArray())
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
