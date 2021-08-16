package io.provenance.digitalcurrency.consortium.stream

private const val ATTRIBUTE_ADMINISTRATOR = "administrator"
private const val ATTRIBUTE_AMOUNT = "amount"
private const val ATTRIBUTE_COINS = "coins"
private const val ATTRIBUTE_DENOM = "denom"
private const val ATTRIBUTE_FROM = "from_address"
private const val ATTRIBUTE_TO = "to_address"

private fun StreamEvent.findAttribute(key: String): String =
    // these are coming from the contract with double quotes on the value
    this.attributes.firstOrNull { it.key == key }?.value?.removeSurrounding("\"") ?: ""

// This is after the burn
// Event(
//  type=provenance.marker.v1.EventMarkerBurn,
//  attributes=[
//          Attribute(key='amount', value='"500"'),
//          Attribute(key='denom', value='"dccbank1.coin"'),
//          Attribute(key='administrator', value='"tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz"')
//      ]
//  )
const val BURN_EVENT = "provenance.marker.v1.EventMarkerBurn"

data class Burn(
    val burnedBy: String,
    val denom: String,
    val amount: String,
    val height: Long,
    val txHash: String
)

typealias Burns = List<Burn>

fun EventBatch.burns(): Burns = events
    .filter { it.eventType == BURN_EVENT }
    .map { event ->
        event.toBurn(this.height)
    }

private fun StreamEvent.toBurn(height: Long): Burn =
    Burn(
        burnedBy = findAttribute(ATTRIBUTE_ADMINISTRATOR),
        denom = findAttribute(ATTRIBUTE_DENOM),
        amount = findAttribute(ATTRIBUTE_AMOUNT),
        height = height,
        txHash = txHash
    )

// this is the coin receipt when Figure Equity Solutions transfers for redemption
// Event(
//  type=provenance.marker.v1.EventMarkerTransfer,
//  attributes=[
//          Attribute(key='amount', value='"500"'),
//          Attribute(key='denom', value='"centiusdx"'),
//          Attribute(key='administrator', value='"tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz"'),
//          Attribute(key='to_address', value='"tp1f6cg6gg3esxnk8jstnlq7htcv5fzrjrf6qvjr7"'),
//          Attribute(key='from_address', value='"tp1mpseerdwvmtx6tcatpknnssfw7f88503vcv56d"')
//      ]
//  )
const val MARKER_TRANSFER_EVENT = "provenance.marker.v1.EventMarkerTransfer"

data class MarkerTransfer(
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val denom: String,
    val height: Long,
    val txHash: String
)

typealias MarkerTransfers = List<MarkerTransfer>

fun EventBatch.transfers(): MarkerTransfers = events
    .filter { it.eventType == MARKER_TRANSFER_EVENT }
    .map { event ->
        event.toMarkerTransfer(this.height)
    }

private fun StreamEvent.toMarkerTransfer(height: Long): MarkerTransfer =
    MarkerTransfer(
        fromAddress = findAttribute(ATTRIBUTE_FROM),
        toAddress = findAttribute(ATTRIBUTE_TO),
        amount = findAttribute(ATTRIBUTE_AMOUNT),
        denom = findAttribute(ATTRIBUTE_DENOM),
        height = height,
        txHash = txHash
    )

// This is after the mint contract is called
// Event(
//  type=provenance.marker.v1.EventMarkerWithdraw,
//  attributes=[
//          Attribute(key='coins', value='"12345centiusdx"'),
//          Attribute(key='denom', value='"centiusdx"'),
//          Attribute(key='administrator', value='"tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz"'),
//          Attribute(key='to_address', value='"tp1kgtu7nw8e5lu3dg8x6cfvpwqxw9h4j3jzlllz7"')
//      ]
//  )

// this is after the redeem contract is called
// Event(
//  type=provenance.marker.v1.EventMarkerWithdraw,
//  attributes=[
//          Attribute(key='coins', value='"500dccbank1.coin"'),
//          Attribute(key='denom', value='"dccbank1.coin"'),
//          Attribute(key='administrator', value='"tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz"'),
//          Attribute(key='to_address', value='"tp1f6cg6gg3esxnk8jstnlq7htcv5fzrjrf6qvjr7"')
//      ]
//  )
const val WITHDRAW_EVENT = "provenance.marker.v1.EventMarkerWithdraw"

data class Withdraw(
    val administrator: String,
    val denom: String,
    val coins: String,
    val toAddress: String,
    val height: Long,
    val txHash: String
)

typealias Withdraws = List<Withdraw>

fun EventBatch.withdraws(): Withdraws = events
    .filter { it.eventType == WITHDRAW_EVENT }
    .map { event ->
        event.toWithdraw(this.height)
    }

private fun StreamEvent.toWithdraw(height: Long): Withdraw =
    Withdraw(
        administrator = findAttribute(ATTRIBUTE_ADMINISTRATOR),
        denom = findAttribute(ATTRIBUTE_DENOM),
        coins = findAttribute(ATTRIBUTE_COINS),
        toAddress = findAttribute(ATTRIBUTE_TO),
        height = height,
        txHash = txHash
    )
