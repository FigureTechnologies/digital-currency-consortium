package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.domain.AddressStatus.INSERTED
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

typealias ART = AddressRegistrationTable

object AddressRegistrationTable : BaseAddressTable(name = "address_registration") {
    val bankAccountUuid = uuid("bank_account_uuid")
    val address = text("address")
    val deleted = offsetDatetime("deleted").nullable()
}

open class AddressRegistrationEntityClass : BaseAddressEntityClass<ART, AddressRegistrationRecord>(ART) {

    fun findByAddress(address: String) = find { ART.address eq address }.firstOrNull()

    fun findByBankAccountUuid(bankAccountUuid: UUID) = find { ART.bankAccountUuid eq bankAccountUuid }.firstOrNull()

    fun insert(
        uuid: UUID = UUID.randomUUID(),
        bankAccountUuid: UUID,
        address: String
    ) = super.insert(uuid, INSERTED).apply {
        this.bankAccountUuid = bankAccountUuid
        this.address = address
    }
}

class AddressRegistrationRecord(uuid: EntityID<UUID>) : BaseAddressRecord(ART, uuid) {
    companion object : AddressRegistrationEntityClass()

    var bankAccountUuid by ART.bankAccountUuid
    var address by ART.address
    var deleted by ART.deleted
    val addressDeregistrations by AddressDeregistrationRecord referrersOn ADT.addressRegistration
}
