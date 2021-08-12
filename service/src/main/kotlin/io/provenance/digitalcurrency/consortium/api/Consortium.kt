package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.math.BigDecimal
import java.util.UUID
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * Request to join the consortium of banks for a digital currency.
 * This should only be done once at the beginning of the relationship with the consortium.
 *
 * @param uuid A unique uuid for this request
 * @param denom The denom you would like to associate with your bank. Must be 8 characters minimum
 * @param maxSupply The maximum dollar amount or your denom you will allow into circulation.
 */
@ApiModel(
    value = "ConsortiumJoinRequest",
    description = """
        Request to join the consortium of banks for a digital currency. 
        This should only be done once at the beginning of the relationship with the consortium.
        """
)
data class ConsortiumJoinRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The denom you would like to associate with your bank",
        required = true,
        allowableValues = "minimum 8 characters"
    )
    @get:NotNull @get:Size(min = 8) val denom: String,

    @ApiModelProperty(
        value = "The maximum dollar amount you will circulate for your denom",
        required = true,
        allowableValues = "Greater than 0"
    )
    @get:NotNull @get:Positive val maxSupply: BigDecimal
)

/**
 * Request to vote on a proposal to the consortium. You must first join the consortium before you can vote.
 *
 * @param uuid A unique uuid for this request
 * @param proposalMemberId The id of the member who submitted the proposal
 * @param vote You vote ('yes' or 'no') on the proposal
 */
@ApiModel(
    value = "ConsortiumVoteRequest",
    description = "Vote 'yes' or 'no' on a proposal to the consortium. You'll need to join the consortium first."
)
data class ConsortiumVoteRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The id of the member who submitted the proposal.",
        required = true
    )
    @get:NotNull val proposalMemberId: String,

    @ApiModelProperty(
        value = "Your vote ('yes' or 'no') on the proposal.",
        required = true,
        allowableValues = "'yes' or 'no'"
    )
    @get:NotNull val vote: VoteChoice
)

/**
 * Accept a proposal to the consortium. You'll need to vote on the proposal first.
 *
 * @param uuid A unique uuid for this request.
 * @param proposalMemberId The id of the member who submitted the proposal
 */
@ApiModel(
    value = "ConsortiumAcceptRequest",
    description = "Accept a proposal to the consortium. You'll need to vote on the proposal first."
)
data class ConsortiumAcceptRequest(

    @ApiModelProperty(
        value = "A unique uuid for this request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "The id of the member who submitted the proposal.",
        required = true
    )
    @get:NotNull val proposalMemberId: String
)

enum class VoteChoice(val value: String) {
    YES("yes"),
    NO("no")
}
