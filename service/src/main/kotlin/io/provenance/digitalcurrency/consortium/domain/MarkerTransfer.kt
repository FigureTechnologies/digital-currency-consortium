package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.util.UUID

typealias MTT = MarkerTransferTable

object MarkerTransferTable : BaseCoinRequestTable(name = "marker_transfer") {
    val fromAddress = text("from_address")
    val toAddress = text("to_address")
    val denom = text("denom")
    val height = long("height")
}

open class MarkerTransferEntityClass : BaseCoinRequestEntityClass<MTT, MarkerTransferRecord>(MTT) {
    fun insert(
        fromAddress: String,
        denom: String,
        amount: String,
        toAddress: String,
        height: Long,
        txHash: String,
        txStatus: TxStatus
    ) =
        super.insert(UUID.randomUUID(), amount.toBigInteger().toUSDAmount()).also {
            it.fromAddress = fromAddress
            it.toAddress = toAddress
            it.denom = denom
            it.height = height
            it.txHash = txHash
            it.status = txStatus
        }

    fun findTxnCompleted() = find { MTT.status eq TxStatus.TXN_COMPLETE }

    fun findTxnCompletedForUpdate(uuid: UUID) = find { (MTT.id eq uuid) and (MTT.status eq TxStatus.TXN_COMPLETE) }.forUpdate()

    fun findByTxHash(txHash: String) = find { MTT.txHash eq txHash }
}

class MarkerTransferRecord(uuid: EntityID<UUID>) : BaseCoinRequestRecord(MTT, uuid) {
    companion object : MarkerTransferEntityClass()

    var fromAddress by MTT.fromAddress
    var toAddress by MTT.toAddress
    var denom by MTT.denom
    var height by MTT.height
}
