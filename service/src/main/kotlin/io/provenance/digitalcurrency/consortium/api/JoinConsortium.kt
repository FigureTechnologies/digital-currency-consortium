package io.provenance.digitalcurrency.consortium.api

import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

data class JoinConsortiumRequest(
    @get:NotNull val uuid: UUID,
    @get:NotNull @get:Positive val maxSupply: BigDecimal
)