// ktlint-disable filename
package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonProperty

data class AcceptRequest(
    @JsonProperty("mint_amount") val mintAmount: String = "0"
)
