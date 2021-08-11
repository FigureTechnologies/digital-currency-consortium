package io.provenance.digitalcurrency.consortium.api

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
data class MintCoinRequest(
    @get:NotNull val uuid: UUID,
    @get:NotNull val bankAccountUUID: UUID,
    @get:NotNull @get:Positive val amount: BigDecimal
)