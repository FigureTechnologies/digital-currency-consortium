package io.provenance.digitalcurrency.report.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.time.OffsetDateTime

abstract class StringEntityClass<out E : Entity<String>>(table: IdTable<String>, entityType: Class<E>? = null) :
    EntityClass<String, E>(table, entityType)

/*
 * Base class for table objects with string id
 */
open class StringIdTable(name: String, columnName: String = "id") : IdTable<String>(name) {
    override val id: Column<EntityID<String>> = text(columnName).entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
}

object CoinMovementTable : StringIdTable(name = "coin_movement", columnName = "txid") {
    val fromAddress = text("from_address")
    val fromMemberId = text("from_member_id")
    val toAddress = text("to_address")
    val toMemberId = text("to_member_id")
    val blockHeight = long("block_height")
    val blockTime = offsetDatetime("block_time").nullable()
    val amount = text("amount")
    val created = offsetDatetime("created")
}

open class CoinMovementEntityClass : StringEntityClass<CoinMovementRecord>(CoinMovementTable) {

    fun insert(
        txHash: String,
        fromAddress: String,
        fromMemberId: String,
        toAddress: String,
        toMemberId: String,
        blockHeight: Long,
        blockTime: OffsetDateTime?,
        amount: String,
    ) = CoinMovementRecord.new(txHash) {
        this.fromAddress = fromAddress
        this.fromMemberId = fromMemberId
        this.toAddress = toAddress
        this.toMemberId = toMemberId
        this.blockHeight = blockHeight
        this.blockTime = blockTime
        this.amount = amount
        this.created = OffsetDateTime.now()
    }
}

class CoinMovementRecord(id: EntityID<String>) : Entity<String>(id) {
    companion object : CoinMovementEntityClass()

    var txHash by CoinMovementTable.id
    var fromAddress by CoinMovementTable.fromAddress
    var fromMemberId by CoinMovementTable.fromMemberId
    var toAddress by CoinMovementTable.toAddress
    var toMemberId by CoinMovementTable.toMemberId
    var blockHeight by CoinMovementTable.blockHeight
    var blockTime by CoinMovementTable.blockTime
    var amount by CoinMovementTable.amount
    var created by CoinMovementTable.created
}
