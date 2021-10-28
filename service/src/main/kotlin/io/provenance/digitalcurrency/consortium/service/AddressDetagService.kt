package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressStatus
import io.provenance.digitalcurrency.consortium.extension.isFailed
import org.springframework.stereotype.Service

@Service
class AddressDetagService(
    private val pbcService: PbcService,
    private val bankClientProperties: BankClientProperties
) {
    private val log = logger()

    fun createEvent(addressDeregistrationRecord: AddressDeregistrationRecord) {
        val address = addressDeregistrationRecord.addressRegistration.address
        val existing = pbcService.getAttributeByTagName(address, bankClientProperties.kycTagName)

        when (existing == null) {
            false -> {
                try {
                    pbcService.deleteAttribute(
                        address = address,
                        tag = bankClientProperties.kycTagName
                    ).also {
                        addressDeregistrationRecord.txHash = it.txResponse.txhash
                        addressDeregistrationRecord.status = AddressStatus.PENDING_TAG
                    }
                } catch (e: Exception) {
                    log.error("Detag failed; it will retry.", e)
                }
            }
            true -> {
                // technically should not happen
                log.warn("Address already detagged - completing")
                addressDeregistrationRecord.status = AddressStatus.COMPLETE
            }
        }
    }

    fun eventComplete(addressDeregistrationRecord: AddressDeregistrationRecord) {
        val address = addressDeregistrationRecord.addressRegistration.address
        val existing = pbcService.getAttributeByTagName(address, bankClientProperties.kycTagName)

        when (existing == null) {
            false -> {
                val response = pbcService.getTransaction(addressDeregistrationRecord.txHash!!)
                if (response == null || response.txResponse.isFailed()) {
                    // need to retry
                    log.info("Detag failed - resetting record to retry")
                    addressDeregistrationRecord.status = AddressStatus.INSERTED
                    addressDeregistrationRecord.txHash = null
                } else {
                    log.info("blockchain detag not done yet - will check next iteration.")
                }
            }
            true -> {
                log.info("detag completed")
                addressDeregistrationRecord.status = AddressStatus.COMPLETE
            }
        }
    }
}
