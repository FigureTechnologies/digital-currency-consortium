package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.NotNull

/**
 * Request to the middleware to burn dcc token from bank member address.
 *
 * @param uuid Unique UUID for this request
 * @param amount The amount in USD to mint to the address.
 */
@ApiModel(
    value = "BurnCoinRequest",
    description = "Request to the middleware to burn dcc token. Must have sufficient dcc coin at bank address."
)
data class BurnCoinRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The amount of fiat in USD to burn.",
        required = true,
        allowableValues = "Greater than 0"
    )
    @get:NotNull
    @get:DecimalMin("0")
    @get:Digits(integer = 12, fraction = 2)
    val amount: BigDecimal
)
