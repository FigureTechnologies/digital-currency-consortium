package io.provenance.digitalcurrency.report.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

typealias SNET = SettlementNetEntryTable

object SettlementNetEntryTable : UUIDTable(name = "settlement_net_entry", columnName = "uuid") {
    val report = reference("settlement_report_uuid", SettlementReportTable)
    val memberId = text("member_id")
    val amount = long("amount")
}

open class SettlementNetEntryEntity : UUIDEntityClass<SettlementNetEntryRecord>(SNET) {

    fun insert(report: SettlementReportRecord, memberId: String, amount: Long) =
        new {
            this.report = report
            this.memberId = memberId
            this.amount = amount
        }
}

class SettlementNetEntryRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : SettlementNetEntryEntity()

    var report by SettlementReportRecord referencedOn SNET.report
    var memberId by SNET.memberId
    var amount by SNET.amount
}
