package io.provenance.digitalcurrency.report.stream

import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.models.Event
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txData
import io.provenance.eventstream.stream.models.extensions.txEvents
import java.time.OffsetDateTime
import java.util.Base64

private const val ATTRIBUTE_ACTION = "action"
private const val ATTRIBUTE_CONTRACT_ADDRESS = "_contract_address"
private const val ATTRIBUTE_AMOUNT = "amount"
private const val ATTRIBUTE_DENOM = "denom"
private const val ATTRIBUTE_SENDER = "sender"
private const val ATTRIBUTE_RECIPIENT = "recipient"
private const val ATTRIBUTE_FROM_MEMBER = "from_member_id"
private const val ATTRIBUTE_TO_MEMBER = "to_member_id"
private const val TRANSFER_ACTION = "transfer"

const val WASM_EVENT = "wasm"

private val base64Decoder = Base64.getDecoder()

typealias Attributes = List<Pair<String, String>>

private fun List<Event>.toAttributes(): Attributes = map {
    String(base64Decoder.decode(it.key)) to (it.value?.run { String(base64Decoder.decode(this)) } ?: "")
}

private fun TxEvent.getAttribute(key: String): String = this.attributes.toAttributes().getAttribute(key)

private fun Attributes.getAttribute(key: String): String =
    // these are coming from the contract with double quotes on the value
    this.firstOrNull { (k, _) -> k == key }?.second?.removeSurrounding("\"") ?: ""

fun BlockData.transfers(contractAddress: String): Transfers =
    blockResult
        .txEvents(block.dateTime()) { block.txData(it) }
        .filter { event ->
            val action = event.getAttribute(ATTRIBUTE_ACTION)
            val contractAddressAttr = event.getAttribute(ATTRIBUTE_CONTRACT_ADDRESS)
            // Filter out older events we do not care about
            val fromMemberId = event.getAttribute(ATTRIBUTE_FROM_MEMBER)
            val toMemberId = event.getAttribute(ATTRIBUTE_TO_MEMBER)

            event.eventType == WASM_EVENT &&
                action == TRANSFER_ACTION &&
                fromMemberId.isNotBlank() &&
                toMemberId.isNotBlank() &&
                contractAddress == contractAddressAttr
        }.map { event -> event.toTransfer() }

typealias Transfers = List<Transfer>

data class Transfer(
    val amount: Long,
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
        amount = getAttribute(ATTRIBUTE_AMOUNT).toLong(),
        denom = getAttribute(ATTRIBUTE_DENOM),
        sender = getAttribute(ATTRIBUTE_SENDER),
        recipient = getAttribute(ATTRIBUTE_RECIPIENT),
        fromMemberId = getAttribute(ATTRIBUTE_FROM_MEMBER),
        toMemberId = getAttribute(ATTRIBUTE_TO_MEMBER),
        height = blockHeight,
        dateTime = blockDateTime,
        txHash = txHash
    )
