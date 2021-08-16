package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

open class BaseRequestTable(name: String) : UUIDTable(name = name, columnName = "uuid") {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
    val created = offsetDatetime("created")
    val updated = offsetDatetime("updated")
}

open class BaseRequestEntityClass<T : BaseRequestTable, R : BaseRequestRecord>(childTable: T) :
    UUIDEntityClass<R>(childTable)

open class BaseRequestRecord(childTable: BaseRequestTable, uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    var coinAmount by childTable.coinAmount
    var fiatAmount by childTable.fiatAmount
    var created by childTable.created
    var updated by childTable.updated
}
