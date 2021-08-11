package io.provenance.digitalcurrency.consortium.api

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
data class DepositFiatRequest(
    @get:NotNull val uuid: UUID,
    @get:NotNull val bankAccountUUID: UUID,
    @get:NotNull @get:Positive val amount: BigDecimal
)