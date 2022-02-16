package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.NotNull

/**
 * Request to the bank to redeem fiat back to another member bank in exchange for dcc coin.
 *
 * @param uuid Unique UUID for this request
 * @param bankMemberAddress The bank member address
 * @param bankMemberName The bank member name
 * @param amount The amount in USD to fiat settle
 */
@ApiModel(
    value = "BankSettlementRequest",
    description = "Request that the bank send fiat to another member bank"
)
data class BankSettlementRequest(
    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The bank member address.",
        required = true
    )
    @get:NotNull val bankMemberAddress: String,

    @ApiModelProperty(
        value = "The bank member address name.",
        required = true
    )
    @get:NotNull val bankMemberName: String,

    @ApiModelProperty(
        value = "The amount of fiat in USD to send to a bank.",
        required = true,
        allowableValues = "Greater than 0"
    )
    @get:NotNull
    @get:DecimalMin("0")
    @get:Digits(integer = 12, fraction = 2)
    val amount: BigDecimal
)
