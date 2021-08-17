package io.provenance.digitalcurrency.consortium.api

import java.math.BigDecimal
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Request to join the consortium. Endpoint will be hit manually on app startup
 *
 * @param name The name of the member bank joining the consortium
 * @param maxSupplyUsd The max supply of dollars the member bank is willing to allow in the consortium
 */
data class JoinConsortiumRequest(
    @get:NotBlank val name: String,
    @get:NotNull
    @get:DecimalMin("0")
    @get:Digits(integer = 12, fraction = 2)
    val maxSupplyUsd: BigDecimal
)
