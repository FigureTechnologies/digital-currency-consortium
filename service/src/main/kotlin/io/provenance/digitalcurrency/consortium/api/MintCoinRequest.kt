package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Request to the middleware to mint coin to the user's address associated with their bank account. The amount
 * will be transferred from the bank's denom to the digital currency and minted to the customer's address.
 *
 * @param uuid Unique UUID for this request
 * @param bankAccountUUID The bank account UUID passed to the middleware in the address registration
 * @param amount The amount in USD to mint to the address.
 */
@ApiModel(
    value = "MintCoinRequest",
    description = "Request to the middleware to mint coin to the user's address associated with their bank account"
)
data class MintCoinRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The uuid of the bank account that the bank passed to the middleware during the address registration.",
        required = true
    )
    @get:NotNull val bankAccountUUID: UUID,

    @ApiModelProperty(
        value = "The amount of fiat in USD to send to the customer.",
        required = true,
        allowableValues = "Greater than 0"
    )
    @get:NotNull @get:Positive val amount: BigDecimal
)
