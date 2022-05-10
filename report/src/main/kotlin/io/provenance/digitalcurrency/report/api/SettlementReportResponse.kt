package io.provenance.digitalcurrency.report.api

import java.time.OffsetDateTime
import java.util.UUID
class SettlementReportResponse(
    val uuid: UUID,
    val fromBlockHeight: Long,
    val toBlockHeight: Long,
    val created: OffsetDateTime
)
