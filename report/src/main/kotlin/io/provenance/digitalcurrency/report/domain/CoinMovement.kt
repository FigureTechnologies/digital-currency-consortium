package io.provenance.digitalcurrency.report.domain

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
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

typealias CMT = CoinMovementTable

object CoinMovementTable : StringIdTable(name = "coin_movement", columnName = "txid") {
    val fromAddress = text("from_address")
    val fromMemberId = text("from_member_id")
    val toAddress = text("to_address")
    val toMemberId = text("to_member_id")
    val blockHeight = long("block_height")
    val blockTime = offsetDatetime("block_time").nullable()
    val amount = long("amount")
    val created = offsetDatetime("created")
}

open class CoinMovementEntityClass : StringEntityClass<CoinMovementRecord>(CMT) {

    private fun findFromSumAmount(fromBlockHeight: Long, toBlockHeight: Long): Map<String, Long> =
        CMT.slice(CMT.amount.sum(), CMT.fromMemberId)
            .select { CMT.blockHeight greaterEq fromBlockHeight and (CMT.blockHeight lessEq toBlockHeight) }
            .groupBy(CMT.fromMemberId)
            .associate { it[CMT.fromMemberId] to -(it[CMT.amount.sum()] ?: 0) }

    private fun findToSumAmount(fromBlockHeight: Long, toBlockHeight: Long) =
        CMT.slice(CMT.amount.sum(), CMT.toMemberId)
            .select { CMT.blockHeight greaterEq fromBlockHeight and (CMT.blockHeight lessEq toBlockHeight) }
            .groupBy(CMT.toMemberId)
            .associate { it[CMT.toMemberId] to (it[CMT.amount.sum()] ?: 0) }

    fun findNetPositions(fromBlockHeight: Long, toBlockHeight: Long): List<Pair<String, Long>> {
        val fromSums = findFromSumAmount(fromBlockHeight, toBlockHeight)
        val toSums = findToSumAmount(fromBlockHeight, toBlockHeight)
        val memberIds = fromSums.keys + toSums.keys

        return memberIds.map { memberId ->
            memberId to (fromSums.getOrDefault(memberId, 0) + toSums.getOrDefault(memberId, 0))
        }
    }

    fun insert(
        txHash: String,
        fromAddress: String,
        fromMemberId: String,
        toAddress: String,
        toMemberId: String,
        blockHeight: Long,
        blockTime: OffsetDateTime?,
        amount: Long,
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

    var txHash by CMT.id
    var fromAddress by CMT.fromAddress
    var fromMemberId by CMT.fromMemberId
    var toAddress by CMT.toAddress
    var toMemberId by CMT.toMemberId
    var blockHeight by CMT.blockHeight
    var blockTime by CMT.blockTime
    var amount by CMT.amount
    var created by CMT.created
}
