package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * Request to grant authz to the smart contract by member bank for a specific coin.
 *
 * @param denom The coin denom to associate with the grant. Defaults to consortium coin.
 * @param quantity The amount of coin to associate with the grant.
 */
@ApiModel(
    value = "GrantCoinRequest",
    description = "Request to the middleware to grant authz to the smart contract by member bank to move restricted coin."
)
data class GrantCoinRequest(

    @ApiModelProperty(
        value = "The coin denom to associate with the grant.",
        required = true
    )
    val denom: String,

    @ApiModelProperty(
        value = "The amount of coin to associate with the grant.",
        required = true
    )
    @get:NotNull
    @get:Min(1)
    val quantity: Long
)

/**
 * Request to grant authz to the smart contract by member bank for a list of coins.
 *
 * @param coins The list of coins
 */
@ApiModel(
    value = "GrantRequest",
    description = "Request to the middleware to grant authz a list of coins to move restricted coin."
)
data class GrantRequest(

    @ApiModelProperty(
        value = "The coins to associate with the grant.",
        required = true
    )
    @Valid
    @Size(min = 1)
    val coins: List<GrantCoinRequest>
)
