package io.provenance.digitalcurrency.consortium.stream

import io.provenance.attribute.v1.Attribute
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
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
) {
    private val log = logger()

    // We're only interested in transfer events from pbc
    private val eventTypes = listOf(MARKER_TRANSFER_EVENT, WITHDRAW_EVENT, BURN_EVENT)

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
                handleEvents(batch.height, batch.burns(), batch.withdraws(), batch.transfers(), batch.mints())
            }

        log.info("Starting event stream at height $lastHeight")

        eventStreamFactory.getStream(eventTypes, lastHeight + 1, responseObserver).streamEvents()

        handleStream(responseObserver, log)
    }

    private fun List<Attribute>.bankUuid(): UUID? = this.find { it.name == bankClientProperties.kycTagName }
        ?.value
        ?.toByteArray()
        ?.toUuid()

    private fun handleCoinMovementEvents(blockHeight: Long, events: Collection<Triple<String, TxType, Any>>) {
        // TODO (steve) is there a grpc endpoint for this?
        val block = rpcClient.fetchBlock(blockHeight).block

        events.forEach { (txHash, _, event) ->
            if (event is MarkerTransfer) {
                log.debug("MarkerTransfer - tx: $txHash from: ${event.fromAddress} to: ${event.toAddress} amount: ${event.amount} denom: ${event.denom}")

                // TODO (steve) implement caching
                // TODO (steve) move to getAttributes by tag name
                val fromAddressBankUuid = pbcService.getAttributes(event.fromAddress).bankUuid()
                val toAddressBankUuid = pbcService.getAttributes(event.toAddress).bankUuid()

                // persist a record of this transaction if either the from or the to address has this bank's attribute
                if (toAddressBankUuid != null && fromAddressBankUuid != null) {
                    // TODO (steve) change to upsert
                    transaction {
                        CoinMovementRecord.insert(
                            txHash = txHash,
                            fromAddress = event.fromAddress,
                            fromAddressBankUuid = fromAddressBankUuid,
                            toAddress = event.toAddress,
                            toAddressBankUuid = toAddressBankUuid,
                            blockHeight = event.height,
                            blockTime = OffsetDateTime.parse(block.header.time),
                            amount = event.amount,
                            denom = event.denom,
                            type = TRANSFER,
                        )
                    }
                }
            } else if (event is Withdraw) {
                log.debug("Withdraw - tx: $txHash to: ${event.toAddress} amount: ${event.coins} denom: ${event.denom}")

                // TODO (steve) implement caching
                // TODO (steve) move to getAttributes by tag name
                val toAddressBankUuid = pbcService.getAttributes(event.toAddress).bankUuid()

                // persist a record of this transaction if the to address has this bank's attribute, the from address will be the SC address
                if (toAddressBankUuid != null) {
                    // TODO (steve) change to upsert
                    transaction {
                        CoinMovementRecord.insert(
                            txHash = txHash,
                            fromAddress = event.administrator,
                            fromAddressBankUuid = null,
                            toAddress = event.toAddress,
                            toAddressBankUuid = toAddressBankUuid,
                            blockHeight = event.height,
                            blockTime = OffsetDateTime.parse(block.header.time),
                            amount = event.coins,
                            denom = event.denom,
                            type = BURN,
                        )
                    }
                }
            } else if (event is Mint) {
                log.debug("Mint - tx: $txHash to: ${event.toAddress} amount: ${event.coins} denom: ${event.denom}")

                // TODO (steve) implement caching
                // TODO (steve) move to getAttributes by tag name
                val toAddressBankUuid = pbcService.getAttributes(event.toAddress).bankUuid()

                // persist a record of this transaction if the to address has this bank's attribute, the from address will be the SC address
                if (toAddressBankUuid != null) {
                    // TODO (steve) change to upsert
                    transaction {
                        CoinMovementRecord.insert(
                            txHash = txHash,
                            fromAddress = event.administrator,
                            fromAddressBankUuid = null,
                            toAddress = event.toAddress,
                            toAddressBankUuid = toAddressBankUuid,
                            blockHeight = event.height,
                            blockTime = OffsetDateTime.parse(block.header.time),
                            amount = event.coins,
                            denom = event.denom,
                            type = MINT,
                        )
                    }
                }
            }
        }
    }

    protected fun handleEvents(blockHeight: Long, burns: Burns, withdraws: Withdraws, markerTransfers: MarkerTransfers, mints: Mints) {
        val events =
            burns.map { Triple(it.txHash, TxType.MARKER_BURN, it) } +
                withdraws.map { Triple(it.txHash, TxType.MARKER_WITHDRAW, it) } +
                markerTransfers.map { Triple(it.txHash, TxType.MARKER_TRANSFER, it) } +
                mints.map { Triple(it.txHash, TxType.MARKER_REDEEM, it) }

        events.forEach { (txHash, type, event) ->
            log.info("event stream found txhash $txHash and type $type [event = {$event}]")
            val txStatusRecord = transaction { TxStatusRecord.findByTxHash(txHash) }
            if (transaction { txStatusRecord.empty() }) {
                if (event is MarkerTransfer &&
                    transaction { MarkerTransferRecord.findByTxHash(txHash) == null } &&
                    event.toAddress == pbcService.managerAddress &&
                    event.denom == serviceProperties.dccDenom
                ) {
                    pbcService.getTransaction(txHash)
                        ?.takeIf {
                            !it.txResponse!!.isFailed()
                        }?.let {
                            log.info("persist received transfer for txhash $txHash")
                            transaction {
                                MarkerTransferRecord.insert(
                                    fromAddress = event.fromAddress,
                                    toAddress = event.toAddress,
                                    denom = event.denom,
                                    coins = event.amount,
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

        handleCoinMovementEvents(blockHeight, events)

        transaction { EventStreamRecord.update(eventStreamId, blockHeight) }
    }
}
