package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModelProperty
import java.time.OffsetDateTime
import java.util.UUID
import javax.validation.constraints.NotNull

data class BalanceRequestItem(
    @ApiModelProperty(
        value = "A unique id for the item in request.",
        required = true
    )
    @get:NotNull val uuid: UUID,

    @ApiModelProperty(
        value = "A blockchain address.",
        required = true
    )
    @get:NotNull val address: String,

    @ApiModelProperty(
        value = "The amount of this record.",
        required = true
    )
    @get:NotNull val amount: String,

    @ApiModelProperty(
        value = "The denom of this record.",
        required = true
    )
    @get:NotNull val denom: String,

    @ApiModelProperty(
        value = "The timestamp of when queried.",
        required = true
    )
    @get:NotNull val timestamp: OffsetDateTime,
)

data class BalanceRequest(
    @ApiModelProperty(
        value = "A unique id for a balance report.",
        required = true
    )
    @get:NotNull val requestUuid: UUID,

    @ApiModelProperty(
        value = "The count of records in this request.",
        required = true
    )
    @get:NotNull val recordCount: Int,

    @ApiModelProperty(
        value = "The page of this request.",
        required = true
    )
    @get:NotNull val page: Int,

    @ApiModelProperty(
        value = "The total pages of this request.",
        required = true
    )
    @get:NotNull val totalPages: Int,

    @ApiModelProperty(
        value = "The record items.",
        required = true
    )
    @get:NotNull val transactions: List<BalanceRequestItem>,
)
