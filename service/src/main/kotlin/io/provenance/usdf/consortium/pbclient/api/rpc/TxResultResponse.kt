package io.provenance.usdf.consortium.pbclient.api.rpc

import io.provenance.usdf.consortium.stream.Event

data class TxResultResponse(
    val code: Int?,
    val data: String?,
    val log: String,
    val info: String,
    val gasWanted: Long,
    val gasUsed: Long,
    val events: List<Event>
)
