package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteJoinRequest(
    val join: JoinRequest? = null
)

data class JoinRequest(
    val denom: String,
    @JsonProperty("max_supply") val maxSupply: String,
    val name: String
)
