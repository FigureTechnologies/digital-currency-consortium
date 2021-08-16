package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import org.jetbrains.exposed.dao.id.EntityID
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

typealias CMT = CoinMintTable

object CoinMintTable : BaseRequestTable(name = "coin_mint") {
    val addressRegistration = reference("address_registration_uuid", AddressRegistrationTable)
    val status = enumerationByName("status", 15, CoinMintStatus::class)
}

open class CoinMintEntityClass : BaseRequestEntityClass<CMT, CoinMintRecord>(CMT) {
    fun insert(
        uuid: UUID,
        addressRegistration: AddressRegistrationRecord,
        fiatAmount: BigDecimal
    ) = new(uuid) {
        this.addressRegistration = addressRegistration
        this.fiatAmount = fiatAmount
        this.coinAmount = fiatAmount.toCoinAmount().toLong()
        this.status = CoinMintStatus.INSERTED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: EntityID<UUID>, newStatus: CoinMintStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() = find { CMT.status inList listOf(CoinMintStatus.INSERTED, CoinMintStatus.PENDING_MINT) }

    fun findForUpdate(uuid: UUID) = find { CMT.id eq uuid }.forUpdate()
}

class CoinMintRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CMT, uuid) {
    companion object : CoinMintEntityClass()

    var addressRegistration by AddressRegistrationRecord referencedOn CMT.addressRegistration
    var status by CMT.status
}

enum class CoinMintStatus {
    INSERTED,
    PENDING_MINT,
    COMPLETE,
    VALIDATION_FAILED
}
