package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.util.UUID

const val MINT = "MINT"
const val TRANSFER = "TRANSFER"
const val BURN = "BURN"

abstract class StringEntityClass<out E : Entity<String>>(table: IdTable<String>, entityType: Class<E>? = null) : EntityClass<String, E>(table, entityType)

/*
 * Base class for table objects with string id
 */
open class StringIdTable(name: String, columnName: String = "id") : IdTable<String>(name) {
    override val id: Column<EntityID<String>> = text(columnName).entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
}

object CoinMovementTable : StringIdTable(name = "coin_movement", columnName = "txid_v2") {
    val legacyTxHash = text("txid").nullable()
    val fromAddress = text("from_addr")
    val fromAddressBankUuid = uuid("from_addr_bank_uuid").nullable()
    val toAddress = text("to_addr")
    val toAddressBankUuid = uuid("to_addr_bank_uuid").nullable()
    val blockHeight = long("block_height")
    val blockTime = offsetDatetime("block_time")
    val amount = text("amount")
    val denom = text("denom")
    val type = text("type")
    val created = offsetDatetime("created")
}

open class CoinMovementEntityClass : StringEntityClass<CoinMovementRecord>(CoinMovementTable) {

    fun findBatch(startBlock: Long, endBlock: Long) = find {
        CoinMovementTable.blockHeight greaterEq(startBlock) and (CoinMovementTable.blockHeight lessEq(endBlock))
    }.toList()

    fun insert(
        txHash: String,
        fromAddress: String,
        fromAddressBankUuid: UUID? = null,
        toAddress: String,
        toAddressBankUuid: UUID? = null,
        blockHeight: Long,
        blockTime: OffsetDateTime,
        amount: String,
        denom: String,
        type: String,
    ) = CoinMovementTable.upsertDoNothing("coin_movement_pkey") {
        it[id] = txHash
        it[this.fromAddress] = fromAddress
        it[this.fromAddressBankUuid] = fromAddressBankUuid
        it[this.toAddress] = toAddress
        it[this.toAddressBankUuid] = toAddressBankUuid
        it[this.blockHeight] = blockHeight
        it[this.blockTime] = blockTime
        it[this.amount] = amount
        it[this.denom] = denom
        it[this.type] = type
        it[this.created] = OffsetDateTime.now()
    }
}

class CoinMovementRecord(id: EntityID<String>) : Entity<String>(id) {
    companion object : CoinMovementEntityClass()

    var _txHashV2 by CoinMovementTable.id
    var _txHash by CoinMovementTable.legacyTxHash
    var fromAddress by CoinMovementTable.fromAddress
    var fromAddressBankUuid by CoinMovementTable.fromAddressBankUuid
    var toAddress by CoinMovementTable.toAddress
    var toAddressBankUuid by CoinMovementTable.toAddressBankUuid
    var blockHeight by CoinMovementTable.blockHeight
    var blockTime by CoinMovementTable.blockTime
    var amount by CoinMovementTable.amount
    var denom by CoinMovementTable.denom
    var type by CoinMovementTable.type
    var created by CoinMovementTable.created

    fun txHash(): String = _txHash ?: _txHashV2.value
}
