package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import java.time.OffsetDateTime
import java.util.UUID

typealias MTT = MarkerTransferTable

object MarkerTransferTable : BaseRequestTable(name = "marker_transfer") {
    val fromAddress = text("from_address")
    val toAddress = text("to_address")
    val denom = text("denom")
    val height = long("height")
    val txHash = text("tx_hash")
    val status = enumerationByName("status", 10, MarkerTransferStatus::class)
}

open class MarkerTransferEntityClass : BaseRequestEntityClass<MTT, MarkerTransferRecord>(MTT) {
    fun insert(fromAddress: String, denom: String, coins: String, toAddress: String, height: Long, txHash: String) =
        new(UUID.randomUUID()) {
            this.fromAddress = fromAddress
            this.toAddress = toAddress
            this.coinAmount = coins.toLong()
            fiatAmount = coins.toBigInteger().toUSDAmount()
            this.denom = denom
            this.height = height
            this.txHash = txHash
            this.status = MarkerTransferStatus.INSERTED
            this.created = OffsetDateTime.now()
            this.updated = OffsetDateTime.now()
        }

    fun updateStatus(uuid: EntityID<UUID>, newStatus: MarkerTransferStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() = find { MTT.status eq MarkerTransferStatus.INSERTED }

    fun findForUpdate(uuid: UUID) = find { MTT.id eq uuid }.forUpdate()

    fun findByTxHash(txHash: String) = find { MTT.txHash eq txHash }.firstOrNull()
}

class MarkerTransferRecord(uuid: EntityID<UUID>) : BaseRequestRecord(MTT, uuid) {
    companion object : MarkerTransferEntityClass()

    var fromAddress by MTT.fromAddress
    var toAddress by MTT.toAddress
    var denom by MTT.denom
    var height by MTT.height
    var txHash by MTT.txHash
    var status by MTT.status
}

enum class MarkerTransferStatus {
    INSERTED,
    COMPLETE
}
