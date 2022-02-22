package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.OffsetDateTime

/**
 * Consortium member bank details.
 *
 * @param id A member bank address and identifier.
 * @param name A member bank name.
 * @param supply A member bank supply of minted reserve token in USD.
 * @param maxSupply A member bank maximum bank supply mintable in USD.
 * @param escrowedSupply A member bank's fiat available in USD.
 * @param denom A member bank reserve token denomination.
 * @param joined Timestamp member bank joined consortium.
 * @param weight A member bank's weight for member voting.
 */
@ApiModel(
    value = "MemberResponse",
    description = "Response for member bank details."
)
data class MemberResponse(
    @ApiModelProperty(
        value = "A member bank address and identifier.",
        required = true
    )
    val id: String,

    @ApiModelProperty(
        value = "A member bank name.",
        required = true
    )
    val name: String,

    @ApiModelProperty(
        value = "A member bank supply of minted reserve token in USD.",
        required = true
    )
    val supply: String,

    @ApiModelProperty(
        value = "A member bank maximum bank supply mintable in USD.",
        required = true
    )
    val maxSupply: String,

    @ApiModelProperty(
        value = "A member bank's fiat available in USD.",
        required = true
    )
    val escrowedSupply: String,

    @ApiModelProperty(
        value = "A member bank reserve token denomination.",
        required = true
    )
    val denom: String,

    @ApiModelProperty(
        value = "Timestamp member bank joined consortium.",
        required = true
    )
    val joined: OffsetDateTime,

    @ApiModelProperty(
        value = "A member bank's weight for member voting.",
        required = true
    )
    val weight: Long
)
