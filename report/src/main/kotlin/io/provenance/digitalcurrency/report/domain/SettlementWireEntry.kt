package io.provenance.digitalcurrency.report.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

typealias SWET = SettlementWireEntryTable

object SettlementWireEntryTable : UUIDTable(name = "settlement_wire_entry", columnName = "uuid") {
    val report = reference("settlement_report_uuid", SettlementReportTable)
    val fromMemberId = text("from_member_id")
    val toMemberId = text("to_member_id")
    val amount = long("amount")
}

open class SettlementWireEntryEntity : UUIDEntityClass<SettlementWireEntryRecord>(SWET) {

    fun insert(report: SettlementReportRecord, fromMemberId: String, toMemberId: String, amount: Long) =
        new {
            this.report = report
            this.fromMemberId = fromMemberId
            this.toMemberId = toMemberId
            this.amount = amount
        }
}

class SettlementWireEntryRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : SettlementWireEntryEntity()

    var report by SettlementReportRecord referencedOn SWET.report
    var fromMemberId by SWET.fromMemberId
    var toMemberId by SWET.toMemberId
    var amount by SWET.amount
}
