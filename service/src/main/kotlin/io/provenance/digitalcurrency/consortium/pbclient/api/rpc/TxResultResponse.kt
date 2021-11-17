package io.provenance.digitalcurrency.consortium.pbclient.api.rpc

import com.fasterxml.jackson.annotation.JsonProperty
import io.provenance.digitalcurrency.consortium.stream.Event

data class TxResultResponse(
    val code: Int?,
    val data: String?,
    val log: String,
    val info: String,
    @JsonProperty("gas_wanted") val gasWanted: Long,
    @JsonProperty("gas_used") val gasUsed: Long,
    val events: List<Event>
)
