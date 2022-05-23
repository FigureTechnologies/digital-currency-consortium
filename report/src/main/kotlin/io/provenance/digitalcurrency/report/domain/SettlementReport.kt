package io.provenance.digitalcurrency.report.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

typealias SRT = SettlementReportTable

object SettlementReportTable : UUIDTable(name = "settlement_report", columnName = "uuid") {
    val fromBlockHeight = long("from_block_height")
    val toBlockHeight = long("to_block_height")
    val created = offsetDatetime("created")
}

open class SettlementReportEntity : UUIDEntityClass<SettlementReportRecord>(SRT) {

    fun insert(fromBlockHeight: Long, toBlockHeight: Long) =
        new {
            this.fromBlockHeight = fromBlockHeight
            this.toBlockHeight = toBlockHeight
            this.created = OffsetDateTime.now()
        }
}

class SettlementReportRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : SettlementReportEntity()

    var fromBlockHeight by SRT.fromBlockHeight
    var toBlockHeight by SRT.toBlockHeight
    var created by SRT.created

    val netEntries by SettlementNetEntryRecord referrersOn SNET.report
    val wireEntries by SettlementWireEntryRecord referrersOn SWET.report
}
