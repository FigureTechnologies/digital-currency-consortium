package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

enum class AddressStatus {
    INSERTED,
    PENDING_TAG,
    COMPLETE,
    ERRORED,
}

open class BaseAddressTable(name: String) : UUIDTable(name = name, columnName = "uuid") {
    val status = enumerationByName("status", 15, AddressStatus::class)
    val txHash = text("tx_hash").nullable()
    val created = offsetDatetime("created")
}

open class BaseAddressEntityClass<T : BaseAddressTable, R : BaseAddressRecord>(
    private val childTable: T
) : UUIDEntityClass<R>(childTable) {

    fun findForUpdate(uuid: UUID) = find { childTable.id eq uuid }.forUpdate()

    fun findPending() = find { childTable.status inList listOf(AddressStatus.INSERTED, AddressStatus.PENDING_TAG) }

    fun insert(uuid: UUID, status: AddressStatus) = new(uuid) {
        this.status = status
        created = OffsetDateTime.now()
    }
}

open class BaseAddressRecord(childTable: BaseAddressTable, uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : AddressRegistrationEntityClass()

    var status by childTable.status
    var txHash by childTable.txHash
    var created by childTable.created
}
