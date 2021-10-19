package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.domain.AddressStatus.INSERTED
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

typealias ADT = AddressDeregistrationTable

object AddressDeregistrationTable : BaseAddressTable(name = "address_dereg") {
    val addressRegistration = reference("address_registration_uuid", ART)
}

open class AddressDeregistrationEntityClass : BaseAddressEntityClass<ADT, AddressDeregistrationRecord>(ADT) {

    fun findByAddressRegistration(addressRegistrationRecord: AddressRegistrationRecord) =
        find { ADT.addressRegistration eq addressRegistrationRecord.id }

    fun insert(addressRegistrationRecord: AddressRegistrationRecord) =
        super.insert(UUID.randomUUID(), INSERTED).apply {
            addressRegistration = addressRegistrationRecord
        }
}

class AddressDeregistrationRecord(uuid: EntityID<UUID>) : BaseAddressRecord(ADT, uuid) {
    companion object : AddressDeregistrationEntityClass()

    var addressRegistration by AddressRegistrationRecord referencedOn ADT.addressRegistration
}
