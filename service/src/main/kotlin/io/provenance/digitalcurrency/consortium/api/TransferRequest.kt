package io.provenance.digitalcurrency.consortium.api

import com.fasterxml.jackson.annotation.JsonIgnore
import io.provenance.hdwallet.bech32.Bech32
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
 * @param uuid Unique UUID for this request.
 * @param bankAccountUuid The uuid of the bank account that the bank passed to the middleware during the address registration to transfer to.
 * @param blockchainAddress The blockchain address to transfer to.
 * @param amount The amount of fiat in USD to transfer to.
 */
@ApiModel(
    value = "TransferRequest",
    description = "Request to the middleware to transfer "
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
    val bankAccountUuid: UUID?,

    @ApiModelProperty(
        value = "The blockchain address to transfer to.",
        required = false
    )
    val blockchainAddress: String?,

    @ApiModelProperty(
        value = "The amount of fiat in USD to transfer to.",
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
    fun hasToAddress() = bankAccountUuid != null || blockchainAddress != null

    @JsonIgnore
    @ApiIgnore
    @AssertTrue(message = "Only bank account or blockchain address can be set")
    fun hasOneToAddress() = !(bankAccountUuid != null && blockchainAddress != null)

    @JsonIgnore
    @ApiIgnore
    @AssertTrue(message = "Invalid blockchain address")
    fun hasValidAddress() = try {
        blockchainAddress?.also { Bech32.decode(it) }
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}
