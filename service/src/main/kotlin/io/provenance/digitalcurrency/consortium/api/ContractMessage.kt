package io.provenance.digitalcurrency.consortium.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(Include.NON_NULL)
data class ExecuteMintRequest(
    val mint: MintRequest? = null
)

data class MintRequest(
    val amount: String,
    val address: String
)

@JsonInclude(Include.NON_NULL)
data class ExecuteRedeemRequest(
    val redeem: RedeemRequest? = null
)

data class RedeemRequest(
    val amount: String,
    @JsonProperty("reserve_denom") val reserveDenom: String,
)

@JsonInclude(Include.NON_NULL)
data class ExecuteBurnRequest(
    val burn: BurnRequest? = null
)

data class BurnRequest(
    val amount: String
)
