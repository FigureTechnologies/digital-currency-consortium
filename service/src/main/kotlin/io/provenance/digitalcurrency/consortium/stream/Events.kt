package io.provenance.digitalcurrency.consortium.stream

import tech.figure.eventstream.stream.clients.BlockData
import tech.figure.eventstream.stream.models.Event
import tech.figure.eventstream.stream.models.TxError
import tech.figure.eventstream.stream.models.TxEvent
import tech.figure.eventstream.stream.models.dateTime
import tech.figure.eventstream.stream.models.txData
import tech.figure.eventstream.stream.models.txErroredEvents
import tech.figure.eventstream.stream.models.txEvents
import java.time.OffsetDateTime
import java.util.Base64

private const val ATTRIBUTE_ACTION = "action"
private const val ATTRIBUTE_CODE_ID = "code_id"
private const val ATTRIBUTE_CONTRACT_ADDRESS = "_contract_address"
private const val ATTRIBUTE_AMOUNT = "amount"
private const val ATTRIBUTE_DENOM = "denom"
private const val ATTRIBUTE_FROM = "from_address"
private const val ATTRIBUTE_WITHDRAW_ADDRESS = "withdraw_address"
private const val ATTRIBUTE_MEMBER_ID = "member_id"
private const val ATTRIBUTE_SENDER = "sender"
private const val ATTRIBUTE_TO = "to_address"
private const val ATTRIBUTE_RECIPIENT = "recipient"
private const val ATTRIBUTE_FROM_MEMBER = "from_member_id"
private const val ATTRIBUTE_TO_MEMBER = "to_member_id"

private const val MINT_ACTION = "mint"
private const val TRANSFER_ACTION = "transfer"
private const val BURN_ACTION = "burn"

const val WASM_EVENT = "wasm"
const val MARKER_TRANSFER_EVENT = "provenance.marker.v1.EventMarkerTransfer"
const val MIGRATE_EVENT = "migrate"

private val base64Decoder = Base64.getDecoder()

typealias Attribute = Pair<String, String>
typealias Attributes = List<Attribute>

private fun List<Event>.toAttributes(): Attributes = map { it.toAttribute() }

private fun Event.toAttribute(): Attribute =
    String(base64Decoder.decode(key)) to (value?.run { String(base64Decoder.decode(this)) } ?: "")

fun List<Event>.splitAttributes(): List<MutableList<Attribute>> =
    this.fold(listOf(mutableListOf())) { accum: List<MutableList<Attribute>>, event: Event ->
        val attribute = event.toAttribute()
        val last = accum.last()

        if (last.any { it.first == attribute.first }) {
            accum.plus(listOf(mutableListOf(attribute)))
        } else {
            last.add(attribute)
            accum
        }
    }

typealias TxEvents = List<TxEvent>
typealias TxErrors = List<TxError>

fun BlockData.txEvents(): TxEvents = blockResult.txEvents(block.dateTime()) { index -> block.txData(index) }

fun BlockData.txErrors(): TxErrors = blockResult.txErroredEvents(block.dateTime()) { index -> block.txData(index) }

private fun TxEvent.getAttribute(key: String): String = this.attributes.toAttributes().getAttribute(key)

private fun Attributes.getAttribute(key: String): String =
    // these are coming from the contract with double quotes on the value
    this.firstOrNull { (k, _) -> k == key }?.second?.removeSurrounding("\"") ?: ""

data class MarkerTransfer(
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val denom: String,
    val height: Long,
    val dateTime: OffsetDateTime?,
    val txHash: String,
)

typealias MarkerTransfers = List<MarkerTransfer>

fun TxEvents.markerTransfers(): MarkerTransfers =
    filter { it.eventType == MARKER_TRANSFER_EVENT }
        .flatMap { event ->
            val nestedAttributes = event.attributes.splitAttributes()

            nestedAttributes.map { it.toMarkerTransfer(event.blockHeight, event.blockDateTime, event.txHash) }
        }

private fun List<Attribute>.toMarkerTransfer(height: Long, dateTime: OffsetDateTime?, txHash: String): MarkerTransfer =
    MarkerTransfer(
        fromAddress = getAttribute(ATTRIBUTE_FROM),
        toAddress = getAttribute(ATTRIBUTE_TO),
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        height = height,
        dateTime = dateTime,
        txHash = txHash,
    )

fun TxEvents.mints(contractAddress: String): Mints =
    filter { event ->
        val action = event.getAttribute(ATTRIBUTE_ACTION)
        val contractAddressAttr = event.getAttribute(ATTRIBUTE_CONTRACT_ADDRESS)
        event.eventType == WASM_EVENT &&
            action == MINT_ACTION &&
            contractAddress == contractAddressAttr
    }
        .map { event -> event.toMint() }

typealias Mints = List<Mint>

data class Mint(
    val amount: String,
    val denom: String,
    val withdrawAddress: String,
    val memberId: String,
    val height: Long,
    val dateTime: OffsetDateTime?,
    val txHash: String,
)

private fun TxEvent.toMint(): Mint =
    Mint(
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        withdrawAddress = getAttribute(ATTRIBUTE_WITHDRAW_ADDRESS),
        memberId = getAttribute(ATTRIBUTE_MEMBER_ID),
        height = blockHeight,
        dateTime = blockDateTime,
        txHash = txHash
    )

fun TxEvents.burns(contractAddress: String): Burns =
    filter { event ->
        val action = event.getAttribute(ATTRIBUTE_ACTION)
        val contractAddressAttr = event.getAttribute(ATTRIBUTE_CONTRACT_ADDRESS)
        event.eventType == WASM_EVENT &&
            action == BURN_ACTION &&
            contractAddress == contractAddressAttr
    }
        .map { event -> event.toBurn() }

typealias Burns = List<Burn>

data class Burn(
    val amount: String,
    val denom: String,
    val memberId: String,
    val height: Long,
    val dateTime: OffsetDateTime?,
    val txHash: String
)

private fun TxEvent.toBurn(): Burn =
    Burn(
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        memberId = getAttribute(ATTRIBUTE_MEMBER_ID),
        height = blockHeight,
        dateTime = blockDateTime,
        txHash = txHash,
    )

fun TxEvents.transfers(contractAddress: String): Transfers =
    filter { event ->
        val action = event.getAttribute(ATTRIBUTE_ACTION)
        val contractAddressAttr = event.getAttribute(ATTRIBUTE_CONTRACT_ADDRESS)
        event.eventType == WASM_EVENT &&
            action == TRANSFER_ACTION &&
            contractAddress == contractAddressAttr
    }
        .map { event -> event.toTransfer() }

typealias Transfers = List<Transfer>

data class Transfer(
    val amount: String,
    val denom: String,
    val sender: String,
    val recipient: String,
    val fromMemberId: String,
    val toMemberId: String,
    val height: Long,
    val dateTime: OffsetDateTime?,
    val txHash: String
)

private fun TxEvent.toTransfer(): Transfer =
    Transfer(
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        sender = getAttribute(ATTRIBUTE_SENDER),
        recipient = getAttribute(ATTRIBUTE_RECIPIENT),
        fromMemberId = getAttribute(ATTRIBUTE_FROM_MEMBER),
        toMemberId = getAttribute(ATTRIBUTE_TO_MEMBER),
        height = blockHeight,
        dateTime = blockDateTime,
        txHash = txHash
    )

fun TxEvents.migrations(contractAddress: String): Migrations =
    filter { event ->
        val contractAddressAttr = event.getAttribute(ATTRIBUTE_CONTRACT_ADDRESS)
        event.eventType == MIGRATE_EVENT &&
            contractAddress == contractAddressAttr
    }
        .map { event -> event.toMigration() }

typealias Migrations = List<Migration>

data class Migration(
    val codeId: String,
    val height: Long,
    val dateTime: OffsetDateTime?,
    val txHash: String
)

private fun TxEvent.toMigration(): Migration =
    Migration(
        codeId = getAttribute(ATTRIBUTE_CODE_ID),
        height = blockHeight,
        dateTime = blockDateTime,
        txHash = txHash
    )
