package io.provenance.digitalcurrency.consortium.api

import com.fasterxml.jackson.annotation.JsonIgnore
import io.provenance.hdwallet.bech32.Bech32
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import springfox.documentation.annotations.ApiIgnore
import java.util.UUID
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.NotNull

/**
 * Register an address associated with an existing bank account.
 * This information will be used to mint coin when deposits are received by the bank
 * and to associate with coin redemptions that result in a fiat deposit.
 *
 * @param bankAccountUuid A UUID assigned by the bank when they receive the notification that an account/wallet
 *                        have been created.
 * @param blockchainAddress The blockchain address from the wallet associated with the bank account. *
 */
@ApiModel(
    value = "RegisterAddressRequest",
    description = "Register a blockchain address associated with an existing bank account. This address will be used for minting/burning coin.",
)
data class RegisterAddressRequest(
    @ApiModelProperty(
        value = "A unique uuid generated and persisted by the bank. " +
            "This will be used for subsequent coin mints (fiat deposits from the customer) and redemptions (fiat deposits to the customer).",
        required = true,
    )
    @get:NotNull
    val bankAccountUuid: UUID,
    @ApiModelProperty(
        value = "The blockchain address associated with the wallet for this bank account.",
        required = true,
    )
    @get:NotNull
    val blockchainAddress: String,
) {

    @JsonIgnore
    @ApiIgnore
    @AssertTrue(message = "Invalid blockchain address")
    fun hasValidAddress() = try {
        Bech32.decode(blockchainAddress)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}
