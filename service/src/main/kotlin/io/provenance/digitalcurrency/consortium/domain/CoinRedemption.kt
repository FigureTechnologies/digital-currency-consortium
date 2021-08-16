package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import java.time.OffsetDateTime
import java.util.UUID

typealias CRT = CoinRedemptionTable

object CoinRedemptionTable : BaseRequestTable(name = "coin_redemption") {
    val addressRegistration = reference("address_registration_uuid", AddressRegistrationTable)
    val status = enumerationByName("status", 15, CoinRedemptionStatus::class)
}

open class CoinRedemptionEntityClass : BaseRequestEntityClass<CRT, CoinRedemptionRecord>(CRT) {
    fun insert(
        addressRegistration: AddressRegistrationRecord,
        coinAmount: Long
    ) = new(UUID.randomUUID()) {
        this.addressRegistration = addressRegistration
        this.coinAmount = coinAmount
        fiatAmount = coinAmount.toBigInteger().toUSDAmount()
        status = CoinRedemptionStatus.INSERTED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: EntityID<UUID>, newStatus: CoinRedemptionStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() =
        find { CRT.status inList listOf(CoinRedemptionStatus.INSERTED, CoinRedemptionStatus.PENDING_REDEEM) }

    fun findForUpdate(uuid: UUID) = find { CRT.id eq uuid }.forUpdate()
}

class CoinRedemptionRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CRT, uuid) {
    companion object : CoinRedemptionEntityClass()

    var addressRegistration by AddressRegistrationRecord referencedOn CRT.addressRegistration
    var status by CRT.status
}

enum class CoinRedemptionStatus {
    INSERTED,
    PENDING_REDEEM,
    REDEEM_COMPLETE,
    PENDING_BURN,
    COMPLETE,
    VALIDATION_FAILED
}
