package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

typealias CMT = CoinMintTable

object CoinMintTable : BaseRequestTable(name = "coin_mint") {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
    val addressRegistration = reference("address_registration_uuid", AddressRegistrationTable)
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
        this.status = TxStatus.QUEUED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: UUID, newStatus: TxStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPendingWithTxHash() = find { (CMT.status eq TxStatus.PENDING) and CMT.txHash.isNotNull() }

    fun findPendingForUpdate(uuid: UUID) = find { (CMT.id eq uuid) and (CMT.status eq TxStatus.PENDING) }.forUpdate()
}

class CoinMintRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CMT, uuid) {
    companion object : CoinMintEntityClass()

    var coinAmount by CMT.coinAmount
    var fiatAmount by CMT.fiatAmount
    var addressRegistration by AddressRegistrationRecord referencedOn CMT.addressRegistration
}
