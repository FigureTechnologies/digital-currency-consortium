package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.NotNull

/**
 * Request to the middleware to take dcc token from bank member address and redeem/burn bank coin.
 *
 * @param uuid Unique UUID for this request
 * @param amount The amount in USD to mint to the address.
 */
@ApiModel(
    value = "MintCoinRequest",
    description = """
        Request to the middleware to redeem and burn dcc token and corresponding bank token. 
        Must have sufficient reserve token available.
    """
)
data class RedeemBurnCoinRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The amount of fiat in USD to mint to the customer's address.",
        required = true,
        allowableValues = "Greater than 0"
    )
    @get:NotNull
    @get:DecimalMin("0")
    @get:Digits(integer = 12, fraction = 2)
    val amount: BigDecimal
)
