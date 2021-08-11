package io.provenance.digitalcurrency.consortium.extension

import cosmos.base.abci.v1beta1.Abci.TxResponse

fun TxResponse.isFailed() = code > 0 && !codespace.isNullOrBlank() && rawLog.isNotBlank() && logsCount == 0
