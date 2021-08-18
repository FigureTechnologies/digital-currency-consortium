package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteRedeemRequest(
    val redeem: RedeemRequest? = null
)

data class RedeemRequest(
    val amount: String,
    @JsonProperty("reserve_denom") val reserveDenom: String
)
