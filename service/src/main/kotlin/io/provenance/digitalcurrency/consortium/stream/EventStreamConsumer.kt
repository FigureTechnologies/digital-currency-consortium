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
import io.provenance.digitalcurrency.consortium.domain.REDEEM
import io.provenance.digitalcurrency.consortium.domain.TRANSFER
import io.provenance.digitalcurrency.consortium.domain.TxRequestViewRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.provenance.digitalcurrency.consortium.service.TxRequestService
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.flows.blockDataFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@NotTest
class EventStreamConsumer(
    private val pbcService: PbcService,
    eventStreamProperties: EventStreamProperties,
    private val serviceProperties: ServiceProperties,
    private val provenanceProperties: ProvenanceProperties,
    private val txRequestService: TxRequestService,
) {
    private val log = logger()

    // The current event stream IDs
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)
    private val coinMovementEventStreamId = UUID.fromString(eventStreamProperties.coinMovementId)

    private val epochHeight = eventStreamProperties.epoch
    private val eventStreamUri = eventStreamProperties.rpcUri
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

        runBlocking {
            val netAdapter = okHttpNetAdapter(eventStreamUri)

            log.info("Starting event stream at height $lastHeight")

            blockDataFlow(
                netAdapter = netAdapter,
                decoderAdapter = moshiDecoderAdapter(),
                from = lastHeight
            ).collect { blockData ->
                val txEvents = blockData.txEvents()
                val txErrors = blockData.txErrors()

                if (txEvents.isNotEmpty()) {
                    handleEvents(
                        txHashes = txEvents.map { it.txHash }.distinct(),
                        migrations = txEvents.migrations(provenanceProperties.contractAddress),
                        transfers = txEvents.transfers(provenanceProperties.contractAddress)
                    )
                }

                if (txErrors.isNotEmpty()) {
                    handleErrors(txHashes = txErrors.map { it.txHash }.distinct())
                }

                transaction { EventStreamRecord.update(eventStreamId, blockData.height) }
            }

            netAdapter.shutdown()
        }
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

        runBlocking {
            val netAdapter = okHttpNetAdapter(eventStreamUri)

            log.info("Starting coin movement event stream at height $lastHeight")

            blockDataFlow(
                netAdapter = netAdapter,
                decoderAdapter = moshiDecoderAdapter(),
                from = lastHeight
            ).collect { blockData ->
                val txEvents = blockData.txEvents()

                handleCoinMovementEvents(
                    mints = txEvents.mints(provenanceProperties.contractAddress),
                    transfers = txEvents.transfers(provenanceProperties.contractAddress),
                    burns = txEvents.burns(provenanceProperties.contractAddress),
                    markerTransfers = txEvents.markerTransfers(),
                )

                transaction { EventStreamRecord.update(coinMovementEventStreamId, blockData.height) }
            }

            netAdapter.shutdown()
        }
    }

    data class MintWrapper(
        val mint: Mint,
        val toAddressBankUuid: UUID?,
    )

    data class RedeemWrapper(
        val transfer: Transfer,
        val fromAddressBankUuid: UUID?,
    )

    data class BurnWrapper(
        val burn: Burn,
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

    fun handleCoinMovementEvents(
        mints: Mints,
        transfers: Transfers,
        burns: Burns,
        markerTransfers: MarkerTransfers
    ) {

        // SC Mint events denote the "on ramp" for a bank user to get coin
        val filteredMints = mints.filter { it.withdrawAddress.isNotEmpty() && it.memberId.isNotEmpty() }
            .mapNotNull { event ->
                log.debug("Mint - tx: $${event.txHash} member: ${event.memberId} withdrawAddr: ${event.withdrawAddress} amount: ${event.amount} denom: ${event.denom}")

                val toAddressBankUuid = event.withdrawAddress.addressToBankUuid()
                // val toAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.withdrawAddress, bankClientProperties.kycTagName)?.bankUuid()

                // persist a record of this transaction if it was created by this bank's address
                if (event.memberId == pbcService.managerAddress) {
                    MintWrapper(event, toAddressBankUuid)
                } else {
                    null
                }
            }

        // SC Transfer events denote the "off ramp" for a bank user to redeem coin for fiat when the recipient is the bank address
        val filteredRedeems = transfers.filter { it.sender.isNotEmpty() && it.recipient.isNotEmpty() }
            .mapNotNull { event ->
                log.debug("Redeem - tx: ${event.txHash} sender: ${event.sender} recipient: ${event.recipient} amount: ${event.amount} denom: ${event.denom}")

                val fromAddressBankUuid = event.sender.addressToBankUuid()
                // val fromAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.sender, bankClientProperties.kycTagName)?.bankUuid()

                when {
                    // ignore redeem transfers not sent to this bank's address
                    event.recipient != pbcService.managerAddress -> null
                    // persist a record of this transaction if the from has this bank's attribute
                    fromAddressBankUuid != null -> RedeemWrapper(event, fromAddressBankUuid)
                    else -> null
                }
            }

        // SC Redeem and Burn events denote the removal of DCC and reserve token from circulation
        val filteredBurns = burns.filter { it.memberId.isNotEmpty() }
            .mapNotNull { event ->
                log.debug("Burn - tx: ${event.txHash} member: ${event.memberId} amount: ${event.amount} denom: ${event.denom}")

                if (event.memberId == pbcService.managerAddress) {
                    BurnWrapper(event)
                } else {
                    null
                }
            }

        // general coin transfers outside of the SC are tracked require the EventMarkerTransfer event
        val filteredTransfers = markerTransfers.filter { it.fromAddress.isNotEmpty() && it.toAddress.isNotEmpty() }
            .filter { it.denom == serviceProperties.dccDenom }
            .mapNotNull { event ->
                log.debug("MarkerTransfer - tx: ${event.txHash} from: ${event.fromAddress} to: ${event.toAddress} amount: ${event.amount} denom: ${event.denom}")

                val fromAddressBankUuid = event.fromAddress.addressToBankUuid()
                val toAddressBankUuid = event.toAddress.addressToBankUuid()
                // val fromAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.fromAddress, bankClientProperties.kycTagName)?.bankUuid()
                // val toAddressBankUuid =
                //     pbcService.getAttributeByTagName(event.toAddress, bankClientProperties.kycTagName)?.bankUuid()

                when {
                    // persist a record of this transaction if either the from or the to address has this bank's attribute
                    toAddressBankUuid != null || fromAddressBankUuid != null ->
                        TransferWrapper(event, toAddressBankUuid, fromAddressBankUuid)
                    // persist a record of this transaction if the from or the to is this bank's address
                    event.fromAddress == pbcService.managerAddress || event.toAddress == pbcService.managerAddress ->
                        TransferWrapper(event, null, null)
                    else -> null
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
                    blockTime = checkNotNull(wrapper.mint.dateTime),
                    amount = wrapper.mint.amount,
                    denom = wrapper.mint.denom,
                    type = MINT,
                )
            }

            filteredRedeems.forEach { wrapper ->
                CoinMovementRecord.insert(
                    txHash = wrapper.transfer.txHash.uniqueHash(index++),
                    fromAddress = wrapper.transfer.sender,
                    fromAddressBankUuid = wrapper.fromAddressBankUuid,
                    toAddress = wrapper.transfer.recipient,
                    toAddressBankUuid = null,
                    blockHeight = wrapper.transfer.height,
                    blockTime = checkNotNull(wrapper.transfer.dateTime),
                    amount = wrapper.transfer.amount,
                    denom = wrapper.transfer.denom,
                    type = REDEEM,
                )
            }

            filteredBurns.forEach { wrapper ->
                CoinMovementRecord.insert(
                    txHash = wrapper.burn.txHash.uniqueHash(index++),
                    fromAddress = wrapper.burn.memberId,
                    fromAddressBankUuid = null,
                    toAddress = wrapper.burn.memberId,
                    toAddressBankUuid = null,
                    blockHeight = wrapper.burn.height,
                    blockTime = checkNotNull(wrapper.burn.dateTime),
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
                    blockTime = checkNotNull(wrapper.transfer.dateTime),
                    amount = wrapper.transfer.amount,
                    denom = wrapper.transfer.denom,
                    type = TRANSFER,
                )
            }
        }
    }

    fun handleEvents(txHashes: List<String>, migrations: Migrations, transfers: Transfers) {
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

    fun handleErrors(txHashes: List<String>) {
        // Handle dcc initialized transactions marked as error reset to queued
        txHashes.forEach { txHash ->
            log.info("resetting txn errors for $txHash")
            txRequestService.resetTxns(txHash)
        }
    }
}
