package io.provenance.digitalcurrency.consortium.stream

import io.provenance.attribute.v1.Attribute
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.BURN
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import io.provenance.digitalcurrency.consortium.domain.MINT
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TRANSFER
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.toUuid
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID

@Component
class EventStreamConsumer(
    private val eventStreamFactory: EventStreamFactory,
    private val pbcService: PbcService,
    private val rpcClient: RpcClient,
    private val bankClientProperties: BankClientProperties,
    eventStreamProperties: EventStreamProperties,
    private val serviceProperties: ServiceProperties,
    private val provenanceProperties: ProvenanceProperties,
) {
    private val log = logger()

    // We're only interested in specific wasm events from pbc
    private val eventTypes = listOf(WASM_EVENT, MARKER_TRANSFER_EVENT)

    // The current event stream ID
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)

    private val epochHeight = eventStreamProperties.epoch.toLong()

    // This is scheduled so if the event streaming server or its proxied blockchain daemon node go down,
    // we'll attempt to re-connect after a fixed delay.
    @Scheduled(
        initialDelayString = "\${event_stream.connect.initial_delay.ms}",
        fixedDelayString = "\${event_stream.connect.delay.ms}"
    )
    fun consumeEventStream() {
        // Initialize event stream state and determine start height
        val record = transaction { EventStreamRecord.findById(eventStreamId) }
        val lastHeight = record?.lastBlockHeight
            ?: transaction { EventStreamRecord.insert(eventStreamId, epochHeight) }.lastBlockHeight
        val responseObserver =
            EventStreamResponseObserver<EventBatch> { batch ->
                handleEvents(
                    batch.height,
                    batch.mints(provenanceProperties.contractAddress),
                    batch.burns(provenanceProperties.contractAddress),
                    batch.redemptions(provenanceProperties.contractAddress),
                    batch.transfers(provenanceProperties.contractAddress)
                )

                handleCoinMovementEvents(
                    batch.height,
                    mints = batch.mints(provenanceProperties.contractAddress),
                    burns = batch.transfers(provenanceProperties.contractAddress),
                    transfers = batch.markerTransfers(),
                )

                transaction { EventStreamRecord.update(eventStreamId, batch.height) }
            }

        log.info("Starting event stream at height $lastHeight")

        eventStreamFactory.getStream(eventTypes, lastHeight + 1, responseObserver).streamEvents()

        handleStream(responseObserver, log)
    }

    private fun Attribute.bankUuid(): UUID = this.value.toByteArray().toUuid()

    data class MintWrapper(
        val mint: Mint,
        val toAddressBankUuid: UUID,
    )

    data class BurnWrapper(
        val burn: Transfer,
        val fromAddressBankUuid: UUID,
    )

    data class TransferWrapper(
        val transfer: MarkerTransfer,
        val toAddressBankUuid: UUID?,
        val fromAddressBankUuid: UUID?,
    )

    fun String.uniqueHash(index: Int): String = "$this-$index"

    fun handleCoinMovementEvents(blockHeight: Long, mints: Mints, burns: Transfers, transfers: MarkerTransfers) {
        // TODO (steve) is there a grpc endpoint for this?
        val block = rpcClient.fetchBlock(blockHeight).block

        // SC Mint events denote the "on ramp" for a bank user to get coin
        val filteredMints = mints.filter { it.withdrawAddress.isNotEmpty() && it.memberId.isNotEmpty() }
            .mapNotNull { event ->
                log.debug("Mint - tx: $${event.txHash} member: ${event.memberId} withdrawAddr: ${event.withdrawAddress} amount: ${event.amount} denom: ${event.withdrawDenom}")

                // TODO (steve) implement caching
                val toAddressBankUuid = pbcService.getAttributeByTagName(event.withdrawAddress, bankClientProperties.kycTagName)?.bankUuid()

                // persist a record of this transaction if the to address has this bank's attribute, the from address will be the SC address
                if (toAddressBankUuid != null) {
                    MintWrapper(event, toAddressBankUuid)
                } else {
                    null
                }
            }

        // SC Transfer events denote the "off ramp" for a bank user to redeem coin when the recipient is the bank address
        val filteredBurns = burns.filter { it.sender.isNotEmpty() && it.recipient.isNotEmpty() }
            .mapNotNull { event ->
                log.debug("SC Transfer - tx: ${event.txHash} sender: ${event.sender} recipient: ${event.recipient} amount: ${event.amount} denom: ${event.denom}")

                // TODO (steve) implement caching
                val fromAddressBankUuid = pbcService.getAttributeByTagName(event.sender, bankClientProperties.kycTagName)?.bankUuid()

                // persist a record of this transaction if either the from or the to address has this bank's attribute
                if (event.recipient == pbcService.managerAddress && fromAddressBankUuid != null) {
                    BurnWrapper(event, fromAddressBankUuid)
                } else {
                    null
                }
            }

        // general coin transfers outside of the SC are tracked require the EventMarkerTransfer event
        val filteredTransfers = transfers.filter { it.fromAddress.isNotEmpty() && it.toAddress.isNotEmpty() }
            .filter { it.denom == bankClientProperties.denom }
            .mapNotNull { event ->
                log.debug("MarkerTransfer - tx: ${event.txHash} from: ${event.fromAddress} to: ${event.toAddress} amount: ${event.amount} denom: ${event.denom}")

                // TODO (steve) implement caching
                val fromAddressBankUuid = pbcService.getAttributeByTagName(event.fromAddress, bankClientProperties.kycTagName)?.bankUuid()
                val toAddressBankUuid = pbcService.getAttributeByTagName(event.toAddress, bankClientProperties.kycTagName)?.bankUuid()

                // persist a record of this transaction if either the from or the to address has this bank's attribute
                if (toAddressBankUuid != null || fromAddressBankUuid != null) {
                    TransferWrapper(event, toAddressBankUuid, fromAddressBankUuid)
                } else {
                    null
                }
            }

        transaction {
            var index = 0

            filteredMints.forEach { wrapper ->
                CoinMovementRecord.insert(
                    txHash = wrapper.mint.txHash.uniqueHash(index ++),
                    fromAddress = wrapper.mint.memberId,
                    fromAddressBankUuid = null,
                    toAddress = wrapper.mint.withdrawAddress,
                    toAddressBankUuid = wrapper.toAddressBankUuid,
                    blockHeight = wrapper.mint.height,
                    blockTime = OffsetDateTime.parse(block.header.time),
                    amount = wrapper.mint.amount,
                    denom = wrapper.mint.withdrawDenom,
                    type = MINT,
                )
            }

            filteredBurns.forEach { wrapper ->
                CoinMovementRecord.insert(
                    txHash = wrapper.burn.txHash.uniqueHash(index ++),
                    fromAddress = wrapper.burn.sender,
                    fromAddressBankUuid = wrapper.fromAddressBankUuid,
                    toAddress = wrapper.burn.recipient,
                    toAddressBankUuid = null,
                    blockHeight = wrapper.burn.height,
                    blockTime = OffsetDateTime.parse(block.header.time),
                    amount = wrapper.burn.amount,
                    denom = wrapper.burn.denom,
                    type = BURN,
                )
            }

            filteredTransfers.forEach { wrapper ->
                CoinMovementRecord.insert(
                    txHash = wrapper.transfer.txHash.uniqueHash(index ++),
                    fromAddress = wrapper.transfer.fromAddress,
                    fromAddressBankUuid = wrapper.fromAddressBankUuid,
                    toAddress = wrapper.transfer.toAddress,
                    toAddressBankUuid = wrapper.toAddressBankUuid,
                    blockHeight = wrapper.transfer.height,
                    blockTime = OffsetDateTime.parse(block.header.time),
                    amount = wrapper.transfer.amount,
                    denom = wrapper.transfer.denom,
                    type = TRANSFER,
                )
            }
        }
    }

    fun handleEvents(
        blockHeight: Long,
        mints: Mints,
        burns: Burns,
        redemptions: Redemptions,
        transfers: Transfers
    ) {
        val events =
            mints.map { Triple(it.txHash, TxType.MINT_CONTRACT, it) } +
                burns.map { Triple(it.txHash, TxType.BURN_CONTRACT, it) } +
                redemptions.map { Triple(it.txHash, TxType.REDEEM_CONTRACT, it) } +
                transfers.map { Triple(it.txHash, TxType.TRANSFER_CONTRACT, it) }

        events.forEach { (txHash, type, event) ->
            log.info("event stream found txhash $txHash and type $type [event = {$event}]")
            val txStatusRecord = transaction { TxStatusRecord.findByTxHash(txHash) }
            if (transaction { txStatusRecord.empty() }) {
                if (event is Transfer &&
                    event.recipient == pbcService.managerAddress &&
                    event.denom == serviceProperties.dccDenom &&
                    transaction { MarkerTransferRecord.findByTxHash(txHash) == null }
                ) {
                    pbcService.getTransaction(txHash)
                        ?.takeIf {
                            !it.txResponse!!.isFailed()
                        }?.let {
                            log.info("persist received transfer for txhash $txHash")
                            transaction {
                                MarkerTransferRecord.insert(
                                    fromAddress = event.sender,
                                    toAddress = event.recipient,
                                    denom = event.denom,
                                    amount = event.amount,
                                    height = event.height,
                                    txHash = txHash
                                )
                            }
                        }
                }
            } else {
                transaction {
                    val lockedStatusRecord = txStatusRecord.forUpdate().first()
                    when (lockedStatusRecord.status) {
                        TxStatus.COMPLETE -> log.warn("Tx status already complete uuid:${lockedStatusRecord.id.value}")
                        TxStatus.ERROR -> log.error("Tx status was already error but received a complete uuid:${lockedStatusRecord.id.value}")
                        else -> {
                            val txResponse = pbcService.getTransaction(txHash)?.txResponse
                            when {
                                txResponse == null -> {
                                    log.error("Invalid (NULL) transaction response")
                                    lockedStatusRecord.setStatus(
                                        TxStatus.ERROR,
                                        "Invalid (NULL) transaction response"
                                    )
                                }
                                txResponse.isFailed() -> {
                                    log.error("Transaction failed: $txResponse")
                                    lockedStatusRecord.setStatus(
                                        TxStatus.ERROR,
                                        txResponse.rawLog
                                    )
                                }
                                else -> lockedStatusRecord.setStatus(TxStatus.COMPLETE)
                            }
                        }
                    }
                }
            }
        }
    }
}
