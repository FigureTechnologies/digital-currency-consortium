package io.provenance.digitalcurrency.consortium.domain

import cosmos.base.abci.v1beta1.Abci
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.util.UUID

typealias TST = TxStatusTable

object TxStatusTable : UUIDTable(name = "tx_status", columnName = "uuid") {
    val txHash = text("tx_hash")
    val txRequestUuid = uuid("tx_request_uuid")
    val status = enumerationByName("status", 20, TxStatus::class)
    val type = enumerationByName("type", 20, TxType::class)
    val rawLog = text("raw_log").nullable()
    val height = long("height")
    val created = offsetDatetime("created")
}

open class TxStatusEntityClass(txEventTable: TxStatusTable) : UUIDEntityClass<TxStatusRecord>(txEventTable) {

    fun insert(
        txResponse: Abci.TxResponse,
        txRequestUuid: UUID,
        type: TxType
    ): TxStatusRecord =
        new {
            this.txRequestUuid = txRequestUuid
            this.txHash = txResponse.txhash
            this.status = TxStatus.PENDING
            this.type = type
            this.rawLog = txResponse.rawLog
            this.height = txResponse.height
            this.created = OffsetDateTime.now()
        }

    fun findForUpdate(uuid: UUID) = find { TST.id eq uuid }.forUpdate()

    // TODO this should go awway after marker transfers are handled in batch
    fun findByTxHash(txHash: String) = find { TST.txHash eq txHash }

    fun findAllByTxHashForUpdate(txHash: String): List<TxStatusRecord> =
        find { TST.txHash eq txHash }.forUpdate().toList()

    fun findByTxRequestUuid(txRequestUuid: UUID): List<TxStatusRecord> =
        find { TST.txRequestUuid eq txRequestUuid }.toList()

    fun findByExpired() = find {
        (TST.created lessEq OffsetDateTime.now().minusSeconds(30)).and(TST.status eq TxStatus.PENDING)
    }
}

class TxStatusRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {

    companion object : TxStatusEntityClass(TxStatusTable)

    var txHash by TST.txHash
    var txRequestUuid by TST.txRequestUuid
    var status by TST.status
    var type by TST.type
    var rawLog by TST.rawLog
    var height by TST.height
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

enum class TxType {
    TRANSFER_CONTRACT,
    MINT_CONTRACT,
    REDEEM_CONTRACT,
    BURN_CONTRACT,
    MIGRATION
}
