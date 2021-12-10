package io.provenance.digitalcurrency.consortium.extension

import cosmos.base.abci.v1beta1.Abci.TxResponse
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.digitalcurrency.consortium.config.PbcException
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.TxResultResponse

private val log = logger("PbcException")

fun TxResultResponse.isFailed() = code != null && code > 0 && codespace.isNullOrBlank() && log.isBlank()
fun TxResponse.isFailed() = code > 0 && !codespace.isNullOrBlank() && rawLog.isNotBlank() && logsCount == 0
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
