package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime
import java.util.UUID

typealias ART = AddressRegistrationTable

object AddressRegistrationTable : UUIDTable(name = "address_registration", columnName = "uuid") {
    val bankAccountUuid = uuid("bank_account_uuid")
    val address = text("address")
    val created = offsetDatetime("created")
}

open class AddressRegistrationEntityClass : UUIDEntityClass<AddressRegistrationRecord>(ART) {

    fun findByAddress(address: String) = find { ART.address eq address }.firstOrNull()

    fun findByBankAccountUuid(bankAccountUuid: UUID) = find { ART.bankAccountUuid eq bankAccountUuid }.firstOrNull()

    fun insert(
        uuid: UUID = UUID.randomUUID(),
        bankAccountUuid: UUID,
        address: String
    ) = new(uuid) {
        this.bankAccountUuid = bankAccountUuid
        this.address = address
        this.created = OffsetDateTime.now()
    }
}

class AddressRegistrationRecord(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : AddressRegistrationEntityClass()

    var bankAccountUuid by ART.bankAccountUuid
    var address by ART.address
    var created by ART.created
}
