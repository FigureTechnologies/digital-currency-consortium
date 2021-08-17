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
    @JsonProperty("reserve_denom") val reserveDenom: String
)

@JsonInclude(Include.NON_NULL)
data class ExecuteBurnRequest(
    val burn: BurnRequest? = null
)

data class BurnRequest(
    val amount: String
)

@JsonInclude(Include.NON_NULL)
data class ExecuteJoinRequest(
    val join: JoinRequest? = null
)

data class JoinRequest(
    val denom: String,
    @JsonProperty("max_supply") val maxSupply: String,
    val name: String
)

@JsonInclude(Include.NON_NULL)
data class ExecuteAcceptRequest(
    val accept: AcceptRequest? = null
)

data class AcceptRequest(
    @JsonProperty("mint_amount") val mintAmount: String = "0"
)
