package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.math.BigDecimal
import java.util.UUID

typealias CTT = CoinTransferTable

object CoinTransferTable : BaseCoinRequestTable(name = "coin_transfer") {
    val address = text("address")
    val addressRegistration = optReference("address_registration_uuid", AddressRegistrationTable)
}

open class CoinTransferEntityClass : BaseCoinRequestEntityClass<CTT, CoinTransferRecord>(CTT) {
    fun findPending() = find { CTT.status inList listOf(TxStatus.PENDING, TxStatus.QUEUED) }

    fun findPendingAmount() = findPending()
        .fold(0L) { acc, record -> acc + record.coinAmount }
        .toBigInteger()

    fun insert(
        uuid: UUID,
        addressRegistration: AddressRegistrationRecord,
        fiatAmount: BigDecimal
    ) = insert(uuid, addressRegistration.address, fiatAmount).apply {
        this.addressRegistration = addressRegistration
    }

    fun insert(
        uuid: UUID,
        address: String,
        fiatAmount: BigDecimal
    ) = super.insert(uuid, fiatAmount).apply {
        this.address = address
    }

    fun findTxnCompleted() = find { CTT.status eq TxStatus.TXN_COMPLETE }

    fun findTxnCompletedForUpdate(uuid: UUID) = find { (CTT.id eq uuid) and (CTT.status eq TxStatus.TXN_COMPLETE) }.forUpdate()
}

class CoinTransferRecord(uuid: EntityID<UUID>) : BaseCoinRequestRecord(CTT, uuid) {
    companion object : CoinTransferEntityClass()

    var address by CTT.address
    var addressRegistration by AddressRegistrationRecord optionalReferencedOn CTT.addressRegistration
}
