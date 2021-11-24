package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

typealias TRV = TxRequestView

object TxRequestView : UUIDTable(name = "tx_request_view", columnName = "uuid") {
    val status = enumerationByName("status", 10, TxStatus::class)
    val type = enumerationByName("type", 6, TxRequestType::class)
    val txHash = text("tx_hash").nullable()
    val timeoutHeight = long("timeout_height").nullable()
    val created = offsetDatetime("created")
    val updated = offsetDatetime("updated")
}

open class TxRequestViewEntityClass : UUIDEntityClass<TxRequestViewRecord>(TRV) {

    fun findQueued(limit: Int = 50) = find { TRV.status eq TxStatus.QUEUED }.limit(limit)

    fun findByTxHash(txHash: String) = find { TRV.txHash eq txHash }.toList()
}

class TxRequestViewRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : TxRequestViewEntityClass()

    var status by TRV.status
    var type by TRV.type
    var txHash by TRV.txHash
    var timeoutHeight by TRV.timeoutHeight
    var created by TRV.created
    var updated by TRV.updated
}
