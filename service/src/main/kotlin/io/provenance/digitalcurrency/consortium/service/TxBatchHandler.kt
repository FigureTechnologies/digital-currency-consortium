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
import io.provenance.digitalcurrency.consortium.domain.CoinTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxRequestType
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
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
    private val maxBatchSize = provenanceProperties.maxBatchSize

    @Scheduled(
        initialDelayString = "\${queue.batch_initial_delay_ms}",
        fixedRateString = "\${queue.batch_polling_delay_ms}"
    )
    fun batchQueuedTxns() {
        // Waiting for last tx committed to completed before we execute another one
        var waitingForCommit: Boolean
        do {
            Thread.sleep(100)
            waitingForCommit = transaction { !TxRequestViewRecord.findPending().empty() }
        } while (waitingForCommit)

        val requests = transaction { TxRequestViewRecord.findQueued(limit = maxBatchSize).map { it.id to it.type } }
            .mapNotNull { (id, type) ->
                when (type) {
                    TxRequestType.MINT -> transaction {
                        CoinMintRecord.findById(id)!!
                            .takeIf { it.address == pbcService.managerAddress || it.addressRegistration!!.isActive() }
                            ?.let { it to buildExecuteContractMessage(it.getExecuteContractMessage()) }
                            ?: run {
                                log.error("Coin mint $id skipped due to inactive address registration")
                                null
                            }
                    }
                    TxRequestType.BURN -> transaction {
                        // TODO - add extra safety for insufficient coin
                        CoinBurnRecord.findById(id)!!
                            .let { it to buildExecuteContractMessage(it.getExecuteContractMessage()) }
                    }
                    TxRequestType.TRANSFER -> transaction {
                        // TODO - add extra safety for insufficient coin
                        CoinTransferRecord.findById(id)!!
                            .let { it to buildExecuteContractMessage(it.getExecuteContractMessage()) }
                    }
                    TxRequestType.TAG -> transaction {
                        AddressRegistrationRecord.findById(id)!!.let {
                            if (!tagExists(it.address)) {
                                it to it.getAddAttributeMessage(
                                    pbcService.managerAddress,
                                    bankClientProperties.kycTagName
                                )
                            } else {
                                // technically should not happen
                                log.error("Address ${it.id} already tagged - skip")
                                null
                            }
                        }
                    }
                    TxRequestType.DETAG -> transaction {
                        AddressDeregistrationRecord.findById(id)!!.let {
                            if (tagExists(it.addressRegistration.address)) {
                                it to it.getDeleteAttributeMessage(
                                    pbcService.managerAddress,
                                    bankClientProperties.kycTagName
                                )
                            } else {
                                // technically should not happen
                                log.error("Address ${it.id} already de-tagged - skip")
                                null
                            }
                        }
                    }
                }
            }

        val (records, messages) = requests.unzip()
        if (messages.isNotEmpty()) {
            log.info("Executing tx requests:${messages.size}")

            val timeoutHeight = pbcTimeoutService.getBlockTimeoutHeight()
            try {
                val txResponse = pbcService.broadcastBatch(messages, timeoutHeight).txResponse
                when (txResponse.code) {
                    0 -> txResponse.txhash
                    else -> {
                        log.error("Error executing match raw:${txResponse.rawLog}")
                        null
                    }
                }
            } catch (e: Exception) {
                log.error("Error submitting batch of messages", e)
                null
            }?.let { txHash ->
                log.info("Broadcast batch result $txHash")
                records.forEach { baseRequest ->
                    transaction {
                        // TODO add retry
                        baseRequest.updateToPending(txHash, timeoutHeight)
                    }
                }
            }
        }
    }

    private fun tagExists(address: String): Boolean =
        pbcService.getAttributeByTagName(address, bankClientProperties.kycTagName) != null

    private fun buildExecuteContractMessage(message: ContractMessageI) =
        Tx.MsgExecuteContract.newBuilder()
            .setSender(pbcService.managerAddress)
            .setContract(provenanceProperties.contractAddress)
            .setMsg(mapper.writeValueAsString(message).toByteString()).build()
}
