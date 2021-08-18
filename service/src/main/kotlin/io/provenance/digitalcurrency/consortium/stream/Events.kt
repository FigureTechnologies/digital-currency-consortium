package io.provenance.digitalcurrency.consortium.stream

private const val ATTRIBUTE_ACTION = "action"
private const val ATTRIBUTE_CONTRACT_ADDRESS = "contract_address"
private const val ATTRIBUTE_AMOUNT = "amount"
private const val ATTRIBUTE_DENOM = "denom"
private const val ATTRIBUTE_RESERVE_DENOM = "reserve_denom"
private const val ATTRIBUTE_WITHDRAW_DENOM = "withdraw_denom"
private const val ATTRIBUTE_WITHDRAW_ADDRESS = "withdraw_address"
private const val ATTRIBUTE_MEMBER_ID = "member_id"
private const val ATTRIBUTE_SENDER = "sender"
private const val ATTRIBUTE_RECIPIENT = "recipient"

private const val MINT_ACTION = "mint"
private const val TRANSFER_ACTION = "transfer"
private const val REDEEM_ACTION = "redeem"
private const val BURN_ACTION = "burn"

const val WASM_EVENT = "wasm"

private fun StreamEvent.getAttribute(key: String): String =
    // these are coming from the contract with double quotes on the value
    this.attributes.firstOrNull { it.key == key }?.value?.removeSurrounding("\"") ?: ""

fun EventBatch.mints(): Mints =
    events
        .filter { event ->
            val action = event.getAttribute(ATTRIBUTE_ACTION)
            event.eventType == WASM_EVENT &&
                action == MINT_ACTION
        }.map { event -> event.toMint() }

typealias Mints = List<Mint>

data class Mint(
    val contractAddress: String,
    val amount: String,
    val denom: String,
    val withdrawDenom: String,
    val withdrawAddress: String,
    val memberId: String,
    val height: Long,
    val txHash: String
)

private fun StreamEvent.toMint(): Mint =
    Mint(
        contractAddress = getAttribute(ATTRIBUTE_CONTRACT_ADDRESS),
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        withdrawDenom = getAttribute(ATTRIBUTE_WITHDRAW_DENOM),
        withdrawAddress = getAttribute(ATTRIBUTE_WITHDRAW_ADDRESS),
        memberId = getAttribute(ATTRIBUTE_MEMBER_ID),
        height = height,
        txHash = txHash
    )

fun EventBatch.burns(): Burns =
    events
        .filter { event ->
            val action = event.getAttribute(ATTRIBUTE_ACTION)
            event.eventType == WASM_EVENT &&
                action == BURN_ACTION
        }.map { event -> event.toBurn() }

typealias Burns = List<Burn>

data class Burn(
    val contractAddress: String,
    val amount: String,
    val denom: String,
    val memberId: String,
    val height: Long,
    val txHash: String
)

private fun StreamEvent.toBurn(): Burn =
    Burn(
        contractAddress = getAttribute(ATTRIBUTE_CONTRACT_ADDRESS),
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        memberId = getAttribute(ATTRIBUTE_MEMBER_ID),
        height = height,
        txHash = txHash
    )

fun EventBatch.redemptions(): Redemptions =
    events
        .filter { event ->
            val action = event.getAttribute(ATTRIBUTE_ACTION)
            event.eventType == WASM_EVENT &&
                action == REDEEM_ACTION
        }.map { event -> event.toRedemption() }

typealias Redemptions = List<Redemption>

data class Redemption(
    val contractAddress: String,
    val amount: String,
    val reserveDenom: String,
    val memberId: String,
    val height: Long,
    val txHash: String
)

private fun StreamEvent.toRedemption(): Redemption =
    Redemption(
        contractAddress = getAttribute(ATTRIBUTE_CONTRACT_ADDRESS),
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        reserveDenom = getAttribute(ATTRIBUTE_RESERVE_DENOM),
        memberId = getAttribute(ATTRIBUTE_MEMBER_ID),
        height = height,
        txHash = txHash
    )

fun EventBatch.transfers(): Transfers =
    events
        .filter { event ->
            val action = event.getAttribute(ATTRIBUTE_ACTION)
            event.eventType == WASM_EVENT &&
                action == TRANSFER_ACTION
        }.map { event -> event.toTransfer() }

typealias Transfers = List<Transfer>

data class Transfer(
    val contractAddress: String,
    val amount: String,
    val denom: String,
    val sender: String,
    val recipient: String,
    val height: Long,
    val txHash: String
)

private fun StreamEvent.toTransfer(): Transfer =
    Transfer(
        contractAddress = getAttribute(ATTRIBUTE_CONTRACT_ADDRESS),
        amount = getAttribute(ATTRIBUTE_AMOUNT),
        denom = getAttribute(ATTRIBUTE_DENOM),
        sender = getAttribute(ATTRIBUTE_SENDER),
        recipient = getAttribute(ATTRIBUTE_RECIPIENT),
        height = height,
        txHash = txHash
    )
