package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.math.BigDecimal
import java.util.UUID

typealias CMT = CoinMintTable

object CoinMintTable : BaseCoinRequestTable(name = "coin_mint") {
    val address = text("address")
    val addressRegistration = optReference("address_registration_uuid", AddressRegistrationTable)
}

open class CoinMintEntityClass : BaseCoinRequestEntityClass<CMT, CoinMintRecord>(CMT) {
    fun insert(
        uuid: UUID,
        addressRegistration: AddressRegistrationRecord,
        fiatAmount: BigDecimal,
    ) = insert(uuid, addressRegistration.address, fiatAmount).apply {
        this.addressRegistration = addressRegistration
    }

    fun insert(
        uuid: UUID,
        address: String,
        fiatAmount: BigDecimal,
    ) = super.insert(uuid, fiatAmount).apply {
        this.address = address
    }

    fun findTxnCompleted() = find { CMT.status eq TxStatus.TXN_COMPLETE }

    fun findTxnCompletedForUpdate(uuid: UUID) = find { (CMT.id eq uuid) and (CMT.status eq TxStatus.TXN_COMPLETE) }.forUpdate()
}

class CoinMintRecord(uuid: EntityID<UUID>) : BaseCoinRequestRecord(CMT, uuid) {
    companion object : CoinMintEntityClass()

    var address by CMT.address
    var addressRegistration by AddressRegistrationRecord optionalReferencedOn CMT.addressRegistration
}
