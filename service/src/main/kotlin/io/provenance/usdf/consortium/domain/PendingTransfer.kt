package io.provenance.usdf.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

typealias PTT = PendingTransferTable

object PendingTransferTable : UUIDTable(name = "pending_transfer", columnName = "uuid") {
    val txHash = text("tx_hash")
    val blockHeight = long("block_height")
    val amountWithDenom = text("amount_with_denom")
    val sender = text("sender")
    val recipient = text("recipient")
    val status = enumerationByName("status", 10, PendingTransferStatus::class)
    val created = offsetDatetime("created")
}

open class PendingTransferClass(pendingTransferTable: PTT) : UUIDEntityClass<PendingTransferRecord>(pendingTransferTable) {
    fun findPending() = find { PTT.status eq PendingTransferStatus.INSERTED }

    fun findForUpdate(uuid: UUID) = find { PTT.id eq uuid }.forUpdate()

    fun insert(txHash: String, blockHeight: Long, amountWithDenom: String, sender: String, recipient: String) =
        PendingTransferRecord.new(UUID.randomUUID()) {
            this.txHash = txHash
            this.blockHeight = blockHeight
            this.amountWithDenom = amountWithDenom
            this.sender = sender
            this.recipient = recipient
            this.status = PendingTransferStatus.INSERTED
            this.created = OffsetDateTime.now()
        }
}

class PendingTransferRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {

    companion object : PendingTransferClass(PTT)

    var txHash by PTT.txHash
    var blockHeight by PTT.blockHeight
    var amountWithDenom by PTT.amountWithDenom
    var sender by PTT.sender
    var recipient by PTT.recipient
    var status by PTT.status
    var created by PTT.created
}

enum class PendingTransferStatus {
    INSERTED,
    EXCEPTION,
}
