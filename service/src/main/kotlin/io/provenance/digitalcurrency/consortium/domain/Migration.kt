package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.api.AlertLevel
import io.provenance.digitalcurrency.consortium.api.AlertRequest
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

typealias MT = MigrationTable

object MigrationTable : UUIDTable(name = "migration", columnName = "uuid") {
    val codeId = text("code_id")
    val txHash = text("tx_hash")
    val created = offsetDatetime("created")
    val sent = offsetDatetime("sent").nullable()
}

open class MigrationEntity : UUIDEntityClass<MigrationRecord>(MT) {
    fun insert(
        codeId: String,
        txHash: String,
        created: OffsetDateTime = OffsetDateTime.now(),
    ) = new {
        this.codeId = codeId
        this.txHash = txHash
        this.created = created
    }

    fun findByTxHash(txHash: String) = find { MT.txHash eq txHash }

    fun findPending() = find { MT.sent.isNull() }

    fun findForUpdate(uuid: UUID) = find { MT.id eq uuid }.forUpdate()
}

class MigrationRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : MigrationEntity()

    var codeId by MT.codeId
    var txHash by MT.txHash
    var created by MT.created
    var sent by MT.sent

    fun markSent() {
        if (sent == null) {
            sent = OffsetDateTime.now()
        }
    }
}

fun MigrationRecord.toAlertRequest() = AlertRequest(
    uuid = id.value,
    alertLevel = AlertLevel.INFO,
    message = "Contract migrated to code id ${this.codeId} with tx hash $txHash",
    timestamp = created,
)
