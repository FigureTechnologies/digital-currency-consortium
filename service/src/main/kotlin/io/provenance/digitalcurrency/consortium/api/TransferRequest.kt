package io.provenance.digitalcurrency.consortium.api

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import springfox.documentation.annotations.ApiIgnore
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.NotNull

/**
 * Request to the middleware to transfer coin to a registered bank account or address.
 *
 * @param uuid Unique UUID for this request
 * @param bankAccountUUID The bank account UUID passed to the middleware in the address registration
 * @param amount The amount in USD to mint to the address.
 */
@ApiModel(
    value = "TransferRequest",
    description = "Request to the middleware to mint coin to the user's address associated with their bank account"
)
data class TransferRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The uuid of the bank account that the bank passed to the middleware during the address registration to transfer to.",
        required = false
    )
    val bankAccountUUID: UUID?,

    @ApiModelProperty(
        value = "The blockchain address to transfer to.",
        required = false
    )
    val blockchainAddress: String?,

    @ApiModelProperty(
        value = "The amount of fiat in USD to mint to the customer's address.",
        required = true,
        allowableValues = "Greater than 0"
    )
    @get:NotNull
    @get:DecimalMin("0")
    @get:Digits(integer = 12, fraction = 2)
    val amount: BigDecimal
) {

    @JsonIgnore
    @ApiIgnore
    @AssertTrue(message = "Bank account or blockchain address must be set")
    fun hasToAddress() = bankAccountUUID != null || blockchainAddress != null

    @JsonIgnore
    @ApiIgnore
    @AssertTrue(message = "Only bank account or blockchain address can be set")
    fun hasOneToAddress() = !(bankAccountUUID != null && blockchainAddress != null)
}
