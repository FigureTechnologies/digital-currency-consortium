package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

typealias EST = EventStreamTable

object EventStreamTable : UUIDTable(name = "event_stream", columnName = "uuid") {
    val lastBlockHeight = long("last_block_height")
    val created = offsetDatetime("created")
    val updated = offsetDatetime("updated").nullable()
}

open class EventStreamEntity : UUIDEntityClass<EventStreamRecord>(EST) {

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

class EventStreamRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : EventStreamEntity()

    var lastBlockHeight by EST.lastBlockHeight
    var created by EST.created
    var updated by EST.updated
}
