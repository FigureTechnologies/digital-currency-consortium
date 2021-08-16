package io.provenance.digitalcurrency.consortium.extension

import cosmos.base.abci.v1beta1.Abci.TxResponse
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.digitalcurrency.consortium.config.PbcException

fun TxResponse.isFailed() = code > 0 && !codespace.isNullOrBlank() && rawLog.isNotBlank() && logsCount == 0

fun ServiceOuterClass.BroadcastTxResponse.throwIfFailed(msg: String): ServiceOuterClass.BroadcastTxResponse {
    if (txResponse.isFailed() || txResponse.txhash.isEmpty()) {
        throw PbcException(msg, txResponse)
    }
    return this
}
