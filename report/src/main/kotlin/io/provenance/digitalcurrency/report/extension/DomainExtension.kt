package io.provenance.digitalcurrency.report.extension

import io.provenance.digitalcurrency.report.api.SettlementReportResponse
import io.provenance.digitalcurrency.report.domain.SettlementReportRecord

fun SettlementReportRecord.toSettlementReportResponse() =
    SettlementReportResponse(
        uuid = id.value,
        fromBlockHeight = fromBlockHeight,
        toBlockHeight = toBlockHeight,
        created = created
    )
