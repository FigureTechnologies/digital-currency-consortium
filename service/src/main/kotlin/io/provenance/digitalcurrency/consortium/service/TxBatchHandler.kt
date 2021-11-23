package io.provenance.digitalcurrency.consortium.service

import com.fasterxml.jackson.databind.ObjectMapper
import cosmwasm.wasm.v1.Tx
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.TxRequestType
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.getAddAttributeMessage
import io.provenance.digitalcurrency.consortium.extension.getDeleteAttributeMessage
import io.provenance.digitalcurrency.consortium.extension.getExecuteContractMessage
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.messages.ContractMessageI
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class TxBatchHandler(
    private val pbcService: PbcService,
    private val provenanceProperties: ProvenanceProperties,
    private val bankClientProperties: BankClientProperties,
    private val mapper: ObjectMapper,
    private val pbcTimeoutService: PbcTimeoutService,
) {
    private val log = logger()

    @Scheduled(
        initialDelayString = "\${queue.batch_initial_delay_ms}",
        fixedRateString = "\${queue.batch_polling_delay_ms}"
    )
    fun batchQueuedTxns() {
        transaction {
            TxRequestViewRecord.findQueued().mapNotNull { request ->
                when (request.type) {
                    TxRequestType.MINT -> CoinMintRecord.findById(request.id)!!.let {
                        it to buildExecuteContractMessage(it.getExecuteContractMessage())
                    }
                    TxRequestType.REDEEM -> CoinRedemptionRecord.findById(request.id)!!.let {
                        it to buildExecuteContractMessage(it.getExecuteContractMessage(bankClientProperties.denom))
                    }
                    TxRequestType.BURN -> CoinBurnRecord.findById(request.id)!!.let {
                        it to buildExecuteContractMessage(it.getExecuteContractMessage())
                    }
                    TxRequestType.TAG -> AddressRegistrationRecord.findById(request.id)!!.let {
                        if (!tagExists(it.address)) {
                            it to it.getAddAttributeMessage(pbcService.managerAddress, bankClientProperties.kycTagName)
                        } else {
                            // technically should not happen
                            log.warn("Address ${it.id} already tagged - completing")
                            it.status = TxStatus.COMPLETE
                            null
                        }
                    }
                    TxRequestType.DETAG -> AddressDeregistrationRecord.findById(request.id)!!.let {
                        if (tagExists(it.addressRegistration.address)) {
                            it to it.getDeleteAttributeMessage(
                                pbcService.managerAddress,
                                bankClientProperties.kycTagName
                            )
                        } else {
                            // technically should not happen
                            log.warn("Address ${it.id} already de-tagged - completing")
                            it.status = TxStatus.COMPLETE
                            null
                        }
                    }
                }
            }.let {
                val (baseRequestRecords, messages) = it.unzip()
                val timeoutHeight = pbcTimeoutService.getBlockTimeoutHeight()
                try {
                    val response = pbcService.broadcastBatch(messages, timeoutHeight)
                    baseRequestRecords.forEach { baseRequestRecord ->
                        baseRequestRecord.updateToPending(response.txResponse.txhash, timeoutHeight)
                    }
                } catch (e: Exception) {
                    log.error("Error submitting batch of messages", e)
                }
            }
        }
    }

    private fun tagExists(address: String): Boolean =
        pbcService.getAttributeByTagName(address, bankClientProperties.kycTagName) == null

    private fun buildExecuteContractMessage(message: ContractMessageI) =
        Tx.MsgExecuteContract.newBuilder()
            .setSender(pbcService.managerAddress)
            .setContract(provenanceProperties.contractAddress)
            .setMsg(mapper.writeValueAsString(message).toByteString()).build()
}
