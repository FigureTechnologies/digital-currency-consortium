package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.util.UUID

typealias MTT = MarkerTransferTable

object MarkerTransferTable : BaseRequestTable(name = "marker_transfer") {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
    val fromAddress = text("from_address")
    val toAddress = text("to_address")
    val denom = text("denom")
    val height = long("height")
}

open class MarkerTransferEntityClass : BaseRequestEntityClass<MTT, MarkerTransferRecord>(MTT) {
    fun insert(fromAddress: String, denom: String, amount: String, toAddress: String, height: Long, txHash: String) =
        super.insert(UUID.randomUUID()).also {
            it.fromAddress = fromAddress
            it.toAddress = toAddress
            it.coinAmount = amount.toLong()
            it.fiatAmount = amount.toBigInteger().toUSDAmount()
            it.denom = denom
            it.height = height
            it.txHash = txHash
        }

    fun updateStatus(uuid: UUID, newStatus: TxStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() = find { MTT.status eq TxStatus.QUEUED }

    fun findPendingForUpdate(uuid: UUID) = find { (MTT.id eq uuid) and (MTT.status eq TxStatus.QUEUED) }.forUpdate()

    fun findByTxHash(txHash: String) = find { MTT.txHash eq txHash }.firstOrNull()
}

class MarkerTransferRecord(uuid: EntityID<UUID>) : BaseRequestRecord(MTT, uuid) {
    companion object : MarkerTransferEntityClass()

    var coinAmount by MTT.coinAmount
    var fiatAmount by MTT.fiatAmount
    var fromAddress by MTT.fromAddress
    var toAddress by MTT.toAddress
    var denom by MTT.denom
    var height by MTT.height
}
