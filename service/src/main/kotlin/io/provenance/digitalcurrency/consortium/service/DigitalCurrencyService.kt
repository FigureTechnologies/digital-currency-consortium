package io.provenance.digitalcurrency.consortium.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.extension.base64encodeToByteString
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class DigitalCurrencyService(
    private val pbcService: PbcService,
    private val bankClientProperties: BankClientProperties,
    private val mapper: ObjectMapper
) {
    private val log = logger()

    fun registerAddress(bankAccountUuid: UUID, blockchainAddress: String) {
        log.info("Registering bank account $bankAccountUuid for address $blockchainAddress with tag ${bankClientProperties.kycTagName}")
        transaction {
            val existing = AddressRegistrationRecord.findByBankAccountUuid(bankAccountUuid)

            check(existing == null) {
                "Bank account $bankAccountUuid is already registered for address ${existing!!.address}"
            }

            // TODO: assume that we will allow one address to have multiple bank account registrations?
            AddressRegistrationRecord.insert(
                bankAccountUuid = bankAccountUuid,
                address = blockchainAddress
            )
        }

        // TODO check to see if attr exists first?
        // TODO exception handling
        pbcService.addAttribute(
            address = blockchainAddress,
            tag = bankClientProperties.kycTagName,
            payload = mapper.writeValueAsString(bankAccountUuid).base64encodeToByteString()
        )
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
