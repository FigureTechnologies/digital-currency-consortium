package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteAcceptRequest(
    val accept: AcceptRequest? = null
)

data class AcceptRequest(
    @JsonProperty("mint_amount") val mintAmount: String = "0"
)
