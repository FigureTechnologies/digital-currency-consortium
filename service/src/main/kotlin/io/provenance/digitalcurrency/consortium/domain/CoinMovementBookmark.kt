package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

object CoinMovementBookmarkTable : UUIDTable(name = "coin_movement_bookmark", columnName = "uuid") {
    val lastBlockHeight = long("last_block_height")
    val created = offsetDatetime("created")
    val updated = offsetDatetime("updated").nullable()
}

open class CoinMovementBookmarkEntity : UUIDEntityClass<CoinMovementBookmarkRecord>(CoinMovementBookmarkTable) {

    fun insert(uuid: UUID, lastBlockHeight: Long) =
        new(uuid) {
            this.lastBlockHeight = lastBlockHeight
            this.created = OffsetDateTime.now()
        }

    fun update(id: UUID, lastBlockHeight: Long) =
        findById(id)?.apply {
            this.lastBlockHeight = lastBlockHeight
            this.updated = OffsetDateTime.now()
        }
}

class CoinMovementBookmarkRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : CoinMovementBookmarkEntity()

    var lastBlockHeight by CoinMovementBookmarkTable.lastBlockHeight
    var created by CoinMovementBookmarkTable.created
    var updated by CoinMovementBookmarkTable.updated
}
