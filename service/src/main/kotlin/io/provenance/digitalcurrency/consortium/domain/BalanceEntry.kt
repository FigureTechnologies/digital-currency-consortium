package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object BalanceEntryTable : UUIDTable(name = "balance_entry", columnName = "uuid") {
    val report = reference("balance_report_uuid", BalanceReportTable)
    val address = text("address")
    val denom = text("denom")
    val amount = text("amount")
    val created = offsetDatetime("created")
}

open class BalanceEntryEntity : UUIDEntityClass<BalanceEntryRecord>(BalanceEntryTable) {

    fun findByReport(report: BalanceReportRecord) = find { BalanceEntryTable.report eq report.id }

    fun insert(
        report: BalanceReportRecord,
        address: String,
        denom: String,
        amount: String,
        created: OffsetDateTime = OffsetDateTime.now()
    ) = new {
        this.report = report
        this.address = address
        this.denom = denom
        this.amount = amount
        this.created = created
    }
}

class BalanceEntryRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : BalanceEntryEntity()

    var report by BalanceReportRecord referencedOn BalanceEntryTable.report
    var address by BalanceEntryTable.address
    var denom by BalanceEntryTable.denom
    var amount by BalanceEntryTable.amount
    var created by BalanceEntryTable.created
}
