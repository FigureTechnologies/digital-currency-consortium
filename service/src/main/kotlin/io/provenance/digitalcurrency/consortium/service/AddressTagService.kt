package io.provenance.digitalcurrency.consortium.service

import com.google.protobuf.ByteString
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressStatus
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.toByteArray
import org.springframework.stereotype.Service

@Service
class AddressTagService(
    private val pbcService: PbcService,
    private val bankClientProperties: BankClientProperties
) {
    private val log = logger()

    fun createEvent(addressRegistrationRecord: AddressRegistrationRecord) {
        val existing =
            pbcService.getAttributeByTagName(addressRegistrationRecord.address, bankClientProperties.kycTagName)
        when (existing == null) {
            true -> {
                try {
                    pbcService.addAttribute(
                        address = addressRegistrationRecord.address,
                        tag = bankClientProperties.kycTagName,
                        payload = ByteString.copyFrom(addressRegistrationRecord.bankAccountUuid.toByteArray())
                    ).also {
                        addressRegistrationRecord.txHash = it.txResponse.txhash
                        addressRegistrationRecord.status = AddressStatus.PENDING_TAG
                    }
                } catch (e: Exception) {
                    log.error("Tag failed; setting registration record status to ERRORED", e)
                    addressRegistrationRecord.status = AddressStatus.ERRORED
                }
            }
            false -> {
                // technically should not happen
                log.warn("Address already tagged - completing")
                addressRegistrationRecord.status = AddressStatus.COMPLETE
            }
        }
    }

    fun eventComplete(addressRegistrationRecord: AddressRegistrationRecord) {
        val existing =
            pbcService.getAttributeByTagName(addressRegistrationRecord.address, bankClientProperties.kycTagName)
        when (existing == null) {
            true -> {
                val response = pbcService.getTransaction(addressRegistrationRecord.txHash!!)
                if (response == null || response.txResponse.isFailed()) {
                    // need to retry
                    log.info("Tag failed - resetting record to retry")
                    addressRegistrationRecord.status = AddressStatus.INSERTED
                    addressRegistrationRecord.txHash = null
                } else {
                    log.info("blockchain tag not done yet - will check next iteration.")
                }
            }
            false -> {
                log.info("tag completed")
                addressRegistrationRecord.status = AddressStatus.COMPLETE
            }
        }
    }
}
