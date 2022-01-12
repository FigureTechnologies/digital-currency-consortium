package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.BURN
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import io.provenance.digitalcurrency.consortium.domain.MINT
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MigrationRecord
import io.provenance.digitalcurrency.consortium.domain.TRANSFER
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
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
@NotTest
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
                handleEvents(
                    batch.height,
                    txHashes = batch.txHashes(),
                    migrations = batch.migrations(provenanceProperties.contractAddress),
                    transfers = batch.transfers(provenanceProperties.contractAddress)
                )

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
                    // TODO - these are really redemption requests, probably need to distinguish between redemption transfers and burns
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

    fun handleEvents(blockHeight: Long, txHashes: List<String>, migrations: Migrations, transfers: Transfers) {
        // Handle dcc initialized transactions marked as complete
        txHashes.forEach { txHash ->
            if (transaction { !TxRequestViewRecord.findByTxHash(txHash).empty() }) {
                log.info("completing other txn events for $txHash")
                txRequestService.completeTxns(txHash)
            }
        }

        // Handle externally initialized transactions
        // Migrations of smart contract
        migrations
            .groupBy { it.txHash }
            .forEach { (txHash, migrations) ->
                transaction {
                    when {
                        !MigrationRecord.findByTxHash(txHash).empty() -> {} // noop - prevent dupe processing
                        else -> migrations.forEach { migration -> insertMigrationEvent(migration) }
                    }
                }
            }

        // Transfers to bank member
        transfers
            .filter { it.recipient == pbcService.managerAddress && it.denom == serviceProperties.dccDenom }
            .groupBy { it.txHash }
            .forEach { (txHash, transfers) ->
                transaction {
                    when {
                        !MarkerTransferRecord.findByTxHash(txHash).empty() -> {} // noop - prevent dupe processing
                        else -> transfers.forEach { transfer -> insertTransferEvent(transfer) }
                    }
                }
            }
    }

    private fun insertMigrationEvent(migration: Migration) {
        log.info("persisting migration record for txhash ${migration.txHash}")
        MigrationRecord.insert(
            codeId = migration.codeId,
            txHash = migration.txHash
        )
    }

    private fun insertTransferEvent(transfer: Transfer) {
        log.info("persist received transfer for txhash ${transfer.txHash}")
        MarkerTransferRecord.insert(
            fromAddress = transfer.sender,
            toAddress = transfer.recipient,
            denom = transfer.denom,
            amount = transfer.amount,
            height = transfer.height,
            txHash = transfer.txHash,
            txStatus = TxStatus.TXN_COMPLETE
        )
    }
}
