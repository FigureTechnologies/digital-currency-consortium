package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.util.UUID

object BalanceReportTable : UUIDTable(name = "balance_report", columnName = "uuid") {
    val created = offsetDatetime("created")
    val completed = offsetDatetime("completed").nullable()
    val sent = offsetDatetime("sent").nullable()
}

open class BalanceReportEntity : UUIDEntityClass<BalanceReportRecord>(BalanceReportTable) {

    fun findForUpdate(uuid: UUID) = find { BalanceReportTable.id eq uuid }.forUpdate()

    fun findPending() = find { BalanceReportTable.sent.isNull() }

    fun findNotSent() = find { BalanceReportTable.completed.isNotNull() and BalanceReportTable.sent.isNull() }

    fun insert() = new { created = OffsetDateTime.now() }
}

class BalanceReportRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : BalanceReportEntity()

    var created by BalanceReportTable.created
    var completed by BalanceReportTable.completed
    var sent by BalanceReportTable.sent

    fun markCompleted() {
        if (completed == null) {
            completed = OffsetDateTime.now()
        }
    }

    fun markSent() {
        if (sent == null) {
            sent = OffsetDateTime.now()
        }
    }
}
