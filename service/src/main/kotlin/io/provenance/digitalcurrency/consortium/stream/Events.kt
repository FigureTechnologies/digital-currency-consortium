package io.provenance.digitalcurrency.consortium.stream

const val NHASH_DENOM = "nhash"

const val TRANSFER_EVENT = "transfer"
const val MESSAGE_EVENT = "message"

private const val ATTRIBUTE_ADMINISTRATOR = "administrator"
private const val ATTRIBUTE_AMOUNT = "amount"
private const val ATTRIBUTE_DENOM = "denom"
private const val ATTRIBUTE_RECIPIENT = "recipient"
private const val ATTRIBUTE_SENDER = "sender"

private fun StreamEvent.hasAttribute(key: String): Boolean = attributes.any { it.key == key }
private fun StreamEvent.findAttribute(key: String): String = this.attributes.firstOrNull { it.key == key }?.value ?: ""
private fun List<StreamEvent>.findAttribute(key: String): String =
    flatMap { it.attributes }.firstOrNull { it.key == key }?.value ?: ""

data class Transfer(
    val sender: String,
    val recipient: String,
    val amount: String,
    val height: Long,
    val txHash: String
)

typealias Transfers = List<Transfer>

fun EventBatch.transfers(): Transfers = events
    .filter { (it.eventType == TRANSFER_EVENT || it.hasAttribute(ATTRIBUTE_SENDER)) && !it.txHash.isNullOrBlank() }
    .groupBy { it.resultIndex }
    .flatMap { it.value.chunked(2) }
    .map { events ->
        events.toTransfer(this.height)
    }

private fun List<StreamEvent>.toTransfer(height: Long): Transfer =
    Transfer(
        sender = findAttribute(ATTRIBUTE_SENDER),
        recipient = findAttribute(ATTRIBUTE_RECIPIENT),
        amount = findAttribute(ATTRIBUTE_AMOUNT),
        height = height,
        txHash = getTxHash(TRANSFER_EVENT)
    )

private fun List<StreamEvent>.getTxHash(eventType: String): String =
    find { it.eventType == eventType }?.txHash ?: ""
