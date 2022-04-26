package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.OffsetDateTime

/**
 * Consortium member bank details.
 *
 * @param id A member bank address and identifier.
 * @param name A member bank name.
 * @param joined Timestamp member bank joined consortium.
 * @param weight A member bank's weight for member voting.
 * @param kycAttributes KYC attributes attributed to this membber.
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
        value = "Timestamp member bank joined consortium.",
        required = true
    )
    val joined: OffsetDateTime,

    @ApiModelProperty(
        value = "A member bank's KYC attributes.",
        required = true
    )
    val kycAttributes: List<String>
)
