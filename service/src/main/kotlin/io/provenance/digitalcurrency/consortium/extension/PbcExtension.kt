package io.provenance.digitalcurrency.consortium.extension

import cosmos.base.abci.v1beta1.Abci.ABCIMessageLog
import cosmos.base.abci.v1beta1.Abci.Attribute
import cosmos.base.abci.v1beta1.Abci.StringEvent
import cosmos.base.abci.v1beta1.Abci.TxResponse
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.digitalcurrency.consortium.config.PbcException
import io.provenance.digitalcurrency.consortium.config.logger

private val log = logger("PbcException")

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

fun ABCIMessageLog.findWithdrawEvent(denom: String) =
    eventsList.firstOrNull { event ->
        event.type.contains("EventMarkerWithdraw") &&
            event.attributesList.any { it.key == "denom" && it.toStringValue() == denom } &&
            event.attributesList.any { it.key == "coins" }
    }

fun StringEvent.coinsAmount(): Long {
    val attribute = attributesList.first { it.key == "coins" }.toStringValue()
    val indexOfDenom = attribute.indexOfFirst { it.isLetter() }

    return attribute.substring(0, indexOfDenom).toLong()
}
