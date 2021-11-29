package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.BURN
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import io.provenance.digitalcurrency.consortium.domain.MINT
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MigrationRecord
import io.provenance.digitalcurrency.consortium.domain.TRANSFER
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.provenance.digitalcurrency.consortium.service.TxRequestService
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
    eventStreamProperties: EventStreamProperties,
    private val serviceProperties: ServiceProperties,
    private val provenanceProperties: ProvenanceProperties,
    private val txRequestService: TxRequestService,
) {
    private val log = logger()

    // We're only interested in specific wasm events from pbc
    private val eventTypes =
        listOf(WASM_EVENT, MARKER_TRANSFER_EVENT, MIGRATE_EVENT, ATTRIBUTE_DELETE_EVENT, ATTRIBUTE_ADD_EVENT)

    // The current event stream IDs
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)
    private val coinMovementEventStreamId = UUID.fromString(eventStreamProperties.coinMovementId)

    private val epochHeight = eventStreamProperties.epoch
    private val coinMovementEpochHeight = eventStreamProperties.coinMovementEpoch

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
                handleEvents(batch)

                transaction { EventStreamRecord.update(eventStreamId, batch.height) }
            }

        log.info("Starting event stream at height $lastHeight")

        eventStreamFactory.getStream(eventTypes, lastHeight + 1, responseObserver).streamEvents()

        handleStream(responseObserver, log)
    }

    // This is scheduled so if the event streaming server or its proxied blockchain daemon node go down,
    // we'll attempt to re-connect after a fixed delay.
    @Scheduled(
        initialDelayString = "\${event_stream.connect.initial_delay.ms}",
        fixedDelayString = "\${event_stream.connect.delay.ms}"
    )
    fun consumeCoinMovementEventStream() {
        // Initialize event stream state and determine start height
        val record = transaction { EventStreamRecord.findById(coinMovementEventStreamId) }
        val lastHeight = record?.lastBlockHeight
            ?: transaction {
                EventStreamRecord.insert(
                    coinMovementEventStreamId,
                    coinMovementEpochHeight
                )
            }.lastBlockHeight
        val responseObserver =
            EventStreamResponseObserver<EventBatch> { batch ->
                handleCoinMovementEvents(
                    batch.height,
                    mints = batch.mints(provenanceProperties.contractAddress),
                    burns = batch.transfers(provenanceProperties.contractAddress),
                    transfers = batch.markerTransfers(),
                )

                transaction { EventStreamRecord.update(coinMovementEventStreamId, batch.height) }
            }

        log.info("Starting coin movement event stream at height $lastHeight")

        eventStreamFactory.getStream(eventTypes, lastHeight + 1, responseObserver).streamEvents()

        handleStream(responseObserver, log)
    }

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

    // private fun io.provenance.attribute.v1.Attribute.bankUuid(): UUID = this.value.toByteArray().toUuid()

    fun String.addressToBankUuid(): UUID? = let {
        transaction { AddressRegistrationRecord.findLatestByAddress(it)?.bankAccountUuid }
    }

    fun handleCoinMovementEvents(blockHeight: Long, mints: Mints, burns: Transfers, transfers: MarkerTransfers) {
        // TODO (steve) is there a grpc endpoint for this?
        val block = rpcClient.fetchBlock(blockHeight).block

        // SC Mint events denote the "on ramp" for a bank user to get coin
        val filteredMints = mints.filter { it.withdrawAddress.isNotEmpty() && it.memberId.isNotEmpty() }
            .mapNotNull { event ->
                log.debug("Mint - tx: $${event.txHash} member: ${event.memberId} withdrawAddr: ${event.withdrawAddress} amount: ${event.amount} denom: ${event.withdrawDenom}")

                val toAddressBankUuid = event.withdrawAddress.addressToBankUuid()
                // val toAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.withdrawAddress, bankClientProperties.kycTagName)?.bankUuid()

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
                log.debug("Burn - tx: ${event.txHash} sender: ${event.sender} recipient: ${event.recipient} amount: ${event.amount} denom: ${event.denom}")

                val fromAddressBankUuid = event.sender.addressToBankUuid()
                // val fromAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.sender, bankClientProperties.kycTagName)?.bankUuid()

                // persist a record of this transaction if either the from or the to address has this bank's attribute
                if (event.recipient == pbcService.managerAddress && fromAddressBankUuid != null) {
                    BurnWrapper(event, fromAddressBankUuid)
                } else {
                    null
                }
            }

        // general coin transfers outside of the SC are tracked require the EventMarkerTransfer event
        val filteredTransfers = transfers.filter { it.fromAddress.isNotEmpty() && it.toAddress.isNotEmpty() }
            .filter { it.denom == serviceProperties.dccDenom }
            .mapNotNull { event ->
                log.debug("MarkerTransfer - tx: ${event.txHash} from: ${event.fromAddress} to: ${event.toAddress} amount: ${event.amount} denom: ${event.denom}")

                val fromAddressBankUuid = event.fromAddress.addressToBankUuid()
                val toAddressBankUuid = event.toAddress.addressToBankUuid()
                // val fromAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.fromAddress, bankClientProperties.kycTagName)?.bankUuid()
                // val toAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.toAddress, bankClientProperties.kycTagName)?.bankUuid()

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
                    txHash = wrapper.mint.txHash.uniqueHash(index++),
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
                    txHash = wrapper.burn.txHash.uniqueHash(index++),
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
                    txHash = wrapper.transfer.txHash.uniqueHash(index++),
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

    fun handleEvents(batch: EventBatch) {
        batch.events.forEach { (txHash, _) ->
            if (transaction { TxRequestViewRecord.findByTxHash(txHash) }.isEmpty() &&
                transaction { CoinRedemptionRecord.findByTxHash(txHash) }.isEmpty()
            ) {
                val parsedEvents = batch.transfers(provenanceProperties.contractAddress)
                    .map { Triple(it.txHash, TxType.TRANSFER_CONTRACT, it) } +
                    batch.migrations(provenanceProperties.contractAddress)
                        .map { Triple(it.txHash, TxType.MIGRATION, it) }

                parsedEvents.forEach { (txHash, type, event) ->
                    log.info("event stream found txhash $txHash and type $type [event = {$event}]")
                    if (event is Migration && transaction { MigrationRecord.findByTxHash(txHash) == null }) {
                        handleMigrationEvent(txHash, event)
                    } else if (event is Transfer &&
                        event.recipient == pbcService.managerAddress &&
                        event.denom == serviceProperties.dccDenom &&
                        transaction { MarkerTransferRecord.findByTxHash(txHash) == null }
                    ) {
                        handleTransferEvent(txHash, event)
                    }
                }
            } else {
                handleAllOtherEvents(txHash, batch.height)
            }
        }
    }

    private fun handleMigrationEvent(txHash: String, migration: Migration) {
        pbcService.getTransaction(txHash)
            ?.takeIf {
                !it.txResponse!!.isFailed()
            }?.let {
                transaction {
                    MigrationRecord.insert(
                        codeId = migration.codeId,
                        txHash = migration.txHash
                    )
                }
            }
    }

    private fun handleTransferEvent(txHash: String, transfer: Transfer) {
        // this should fail if we can't find it here because we received an event for the tx hash to get here
        pbcService.getTransaction(txHash)!!.takeIf {
            !it.txResponse.isFailed()
        }?.let {
            log.info("persist received transfer for txhash $txHash")
            transaction {
                MarkerTransferRecord.insert(
                    fromAddress = transfer.sender,
                    toAddress = transfer.recipient,
                    denom = transfer.denom,
                    amount = transfer.amount,
                    height = transfer.height,
                    txHash = txHash
                )
            }
        }
    }

    // TODO add back in the expired reaper to get pendings in the tx request view
    private fun handleAllOtherEvents(txHash: String, blockHeight: Long) {
        log.info("completing other txn events for $txHash")
        val txResponse = pbcService.getTransaction(txHash)?.txResponse!!
        when (txResponse.isFailed()) {
            true -> {
                log.error("Transactions for $txHash failed: $txResponse")
                txRequestService.resetTxns(txHash, blockHeight)
            }
            false -> txRequestService.completeTxns(txHash)
        }
    }
}
