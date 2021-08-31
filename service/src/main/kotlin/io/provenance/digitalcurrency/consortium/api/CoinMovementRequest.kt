package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.OffsetDateTime
import java.util.UUID
import javax.validation.constraints.NotNull

/**
 * Details of a coin movement for the bank's reporting needs.
 */
@ApiModel(
    value = "CoinMovementRequestItem",
    description = "Details of a particular coin movement sent to the bank."
)
data class CoinMovementRequestItem(
    @ApiModelProperty(
        value = "A unique id for this request.",
        required = true
    )
    @get:NotNull val txId: String,

    @ApiModelProperty(
        value = "A blockchain address.",
        required = true
    )
    @get:NotNull val fromAddress: String,

    @ApiModelProperty(
        value = "The bank uuid associated with the \"from address\" if one exists.",
        required = true
    )
    val fromAddressBankUuid: UUID?,

    @ApiModelProperty(
        value = "A blockchain address.",
        required = true
    )
    @get:NotNull val toAddress: String,

    @ApiModelProperty(
        value = "The bank uuid associated with the \"to address\" if one exists.",
        required = true
    )
    val toAddressBankUuid: UUID?,

    @ApiModelProperty(
        value = "The block this transaction was included in.",
        required = true
    )
    @get:NotNull val blockHeight: String,

    @ApiModelProperty(
        value = "The timestamp as reported in the block header.",
        required = true
    )
    @get:NotNull val timestamp: OffsetDateTime,

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
        value = "The type of this record (MINT|TRANSFER|BURN).",
        required = true
    )
    @get:NotNull val transactionType: String,
)

/**
 * Aggregator of coin movements for the bank's reporting needs
 */
@ApiModel(
    value = "CoinMovementRequest",
    description = "Request that the bank persist coin movement records."
)
data class CoinMovementRequest(
    @ApiModelProperty(
        value = "The count of records in this request.",
        required = true
    )
    @get:NotNull val recordCount: Int,

    @ApiModelProperty(
        value = "The record items.",
        required = true
    )
    @get:NotNull val transactions: List<CoinMovementRequestItem>,
)
