// ktlint-disable filename
package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonProperty

data class JoinRequest(
    val denom: String,
    @JsonProperty("max_supply") val maxSupply: String,
    val name: String
)
