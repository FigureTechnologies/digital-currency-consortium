package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import java.util.UUID

abstract class DatabaseTest {
    fun insertRegisteredAddress(bankAccountUuid: UUID, address: String) =
        AddressRegistrationRecord.insert(
            uuid = UUID.randomUUID(),
            bankAccountUuid = bankAccountUuid,
            address = address
        )
}
