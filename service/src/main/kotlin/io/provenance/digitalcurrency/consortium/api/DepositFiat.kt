package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Request to the bank to deposit fiat to the user's bank account.
 *
 * @param uuid Unique UUID for this request
 * @param bankAccountUUID The bank account UUID passed to the middleware in the address registration
 * @param amount The amount in USD to deposit to the user's bank account
 */
@ApiModel(
    value = "DepositFiatRequest",
    description = "Request that the bank send fiat to the registered bank account"
)
data class DepositFiatRequest(
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