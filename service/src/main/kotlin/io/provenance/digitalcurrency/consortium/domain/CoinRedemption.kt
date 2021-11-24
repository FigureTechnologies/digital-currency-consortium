package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.util.UUID

typealias CRT = CoinRedemptionTable

object CoinRedemptionTable : BaseRequestTable(name = "coin_redemption") {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
    val addressRegistration = reference("address_registration_uuid", AddressRegistrationTable)
}

open class CoinRedemptionEntityClass : BaseRequestEntityClass<CRT, CoinRedemptionRecord>(CRT) {
    fun insert(
        addressRegistration: AddressRegistrationRecord,
        coinAmount: Long
    ) = new(UUID.randomUUID()) {
        this.addressRegistration = addressRegistration
        this.coinAmount = coinAmount
        fiatAmount = coinAmount.toBigInteger().toUSDAmount()
        status = TxStatus.QUEUED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: UUID, newStatus: TxStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() =
        find { CRT.status eq TxStatus.PENDING }

    fun findPendingForUpdate(uuid: UUID) = find { (CRT.id eq uuid) and (CRT.status eq TxStatus.PENDING) }.forUpdate()
}

class CoinRedemptionRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CRT, uuid) {
    companion object : CoinRedemptionEntityClass()

    var coinAmount by CMT.coinAmount
    var fiatAmount by CMT.fiatAmount
    var addressRegistration by AddressRegistrationRecord referencedOn CRT.addressRegistration
}
