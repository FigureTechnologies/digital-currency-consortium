package io.provenance.digitalcurrency.consortium.api

import io.swagger.annotations.ApiModelProperty
import java.time.OffsetDateTime
import java.util.UUID
import javax.validation.constraints.NotNull

data class AlertRequest(
    @ApiModelProperty(
        value = "A unique id for the alert.",
        required = true,
    )
    @get:NotNull
    val uuid: UUID,

    @ApiModelProperty(
        value = "The severity of the alert.",
        required = true,
    )
    @get:NotNull
    val alertLevel: AlertLevel,

    @ApiModelProperty(
        value = "The details of the alert.",
        required = true,
    )
    @get:NotNull
    val message: String,

    @ApiModelProperty(
        value = "The timestamp of the alert.",
        required = true,
    )
    @get:NotNull
    val timestamp: OffsetDateTime,
)

enum class AlertLevel {
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}
