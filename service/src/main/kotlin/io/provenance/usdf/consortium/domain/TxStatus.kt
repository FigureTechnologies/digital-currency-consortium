package io.provenance.usdf.consortium.domain

import cosmos.base.abci.v1beta1.Abci
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

typealias TST = TxStatusTable

object TxStatusTable : UUIDTable(name = "tx_status", columnName = "uuid") {
    val txHash = text("tx_hash")
    val status = enumerationByName("status", 20, TxStatus::class)
    val rawLog = text("raw_log").nullable()
    val created = offsetDatetime("created")
}

open class TxStatusEntityClass(txEventTable: TxStatusTable) : UUIDEntityClass<TxStatusRecord>(txEventTable) {

    fun insert(
        txResponse: Abci.TxResponse,
        status: TxStatus = TxStatus.PENDING
    ): TxStatusRecord =
        new {
            this.txHash = txResponse.txhash
            this.status = status
            this.rawLog = txResponse.rawLog
            this.created = OffsetDateTime.now()
        }

    fun findByTxHash(txHash: String) = find { TST.txHash eq txHash }
}

class TxStatusRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {

    companion object : TxStatusEntityClass(TxStatusTable)

    var txHash by TST.txHash
    var status by TST.status
    var rawLog by TST.rawLog
    var created by TST.created

    fun setStatus(status: TxStatus, msg: String? = null): TxStatusRecord {
        this.status = status
        rawLog = msg
        return this
    }
}

enum class TxStatus {
    PENDING,
    COMPLETE,
    ERROR
}
