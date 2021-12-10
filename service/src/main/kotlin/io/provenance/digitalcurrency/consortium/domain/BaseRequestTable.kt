package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

open class BaseRequestTable(name: String) : UUIDTable(name = name, columnName = "uuid") {
    val status = enumerationByName("status", 15, TxStatus::class)
    val txHash = text("tx_hash").nullable()
    val timeoutHeight = long("timeout_height").nullable()
    val created = offsetDatetime("created")
    val updated = offsetDatetime("updated")
}

open class BaseRequestEntityClass<T : BaseRequestTable, R : BaseRequestRecord>(childTable: T) :
    UUIDEntityClass<R>(childTable) {
    open fun insert(uuid: UUID) = new(uuid) {
        this.status = TxStatus.QUEUED
        created = OffsetDateTime.now()
        updated = OffsetDateTime.now()
    }
}

open class BaseRequestRecord(childTable: BaseRequestTable, uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    var status by childTable.status
    var txHash by childTable.txHash
    var timeoutHeight by childTable.timeoutHeight
    var created by childTable.created
    var updated by childTable.updated

    fun updateToPending(txHash: String, timeoutHeight: Long) {
        status = TxStatus.PENDING
        this.txHash = txHash
        this.timeoutHeight = timeoutHeight
        updated = OffsetDateTime.now()
    }

    fun updateToTxnComplete() {
        status = TxStatus.TXN_COMPLETE
        updated = OffsetDateTime.now()
    }

    fun resetForRetry(blockHeight: Long) {
        if (timeoutHeight == null || timeoutHeight!! < blockHeight) {
            status = TxStatus.QUEUED
            txHash = null
            timeoutHeight = null
            updated = OffsetDateTime.now()
        }
    }
}
