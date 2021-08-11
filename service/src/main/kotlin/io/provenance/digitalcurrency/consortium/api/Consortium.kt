package io.provenance.digitalcurrency.consortium.api

import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.NotNull

data class ConsortiumJoinRequest(
    @get:NotNull val uuid: UUID,
    @get:NotNull val denom: String,
    @get:NotNull val maxSupply: BigDecimal
)

data class ConsortiumVoteRequest(
    @get:NotNull val uuid: UUID,
    @get:NotNull val proposalMemberId: String,
    @get:NotNull val vote: VoteChoice
)

data class ConsortiumAcceptRequest(
    @get:NotNull val uuid: UUID,
    @get:NotNull val proposalMemberId: String
)

enum class VoteChoice(val value: String) {
    YES("yes"),
    NO("no")
}