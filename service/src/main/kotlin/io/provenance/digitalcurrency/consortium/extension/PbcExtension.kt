package io.provenance.digitalcurrency.consortium.extension

import cosmos.base.abci.v1beta1.Abci.ABCIMessageLog
import cosmos.base.abci.v1beta1.Abci.Attribute
import cosmos.base.abci.v1beta1.Abci.StringEvent
import cosmos.base.abci.v1beta1.Abci.TxResponse
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.digitalcurrency.consortium.config.PbcException
import io.provenance.digitalcurrency.consortium.config.logger

private val log = logger("PbcException")
private const val EVENT_MARKER_WITHDRAW_ATTRIBUTE = "EventMarkerWithdraw"
private const val DENOM_KEY = "denom"
private const val COINS_KEY = "coins"

fun TxResponse.isFailed() = code > 0 && !codespace.isNullOrBlank() && rawLog.isNotBlank() && logsCount == 0
fun TxResponse.isSingleTx() = logsCount == 1
fun TxResponse.details() =
    """
        Error Code: $code
        Raw log: $rawLog
        Height: $height
        Tx Hash: $txhash
    """.trimIndent()

fun ServiceOuterClass.BroadcastTxResponse.throwIfFailed(msg: String): ServiceOuterClass.BroadcastTxResponse {
    if (txResponse.isFailed() || txResponse.txhash.isEmpty()) {
        log.error("PBC Response: ${this.txResponse.details()}")
        throw PbcException(msg, txResponse)
    }
    return this
}

fun Attribute.toStringValue() = value.removeSurrounding("\"")

// TODO - wasm event parsing may be a little simpler
fun ABCIMessageLog.findWithdrawEvent(denom: String) =
    eventsList.firstOrNull { event ->
        event.type.contains(EVENT_MARKER_WITHDRAW_ATTRIBUTE) &&
            event.attributesList.any { it.key == DENOM_KEY && it.toStringValue() == denom } &&
            event.attributesList.any { it.key == COINS_KEY }
    }

fun StringEvent.coinsAmount(denom: String): Long =
    attributesList.filter { it.key == COINS_KEY }
        .mapNotNull { attribute ->
            val stringValue = attribute.toStringValue()
            val indexOfDenom = stringValue.indexOfFirst { char -> char.isLetter() }

            if (stringValue.substring(indexOfDenom) == denom) {
                stringValue.substring(0, indexOfDenom).toLong()
            } else null
        }
        .single()
