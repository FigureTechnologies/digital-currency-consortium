package io.provenance.digitalcurrency.consortium.api

import java.util.UUID
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
data class RegisterAccountRequest(
    @get:NotNull val bankAccountUuid: UUID,
    @get:NotNull val blockchainAddress: String
)
