package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.util.UUID

typealias CBT = CoinBurnTable

object CoinBurnTable : BaseRequestTable(name = "coin_burn") {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
    val coinRedemption = (reference("coin_redemption_uuid", CoinRedemptionTable)).nullable()
}

open class CoinBurnEntityClass : BaseRequestEntityClass<CBT, CoinBurnRecord>(CBT) {
    fun insert(
        coinRedemption: CoinRedemptionRecord?,
        coinAmount: Long
    ) = new(UUID.randomUUID()) {
        this.coinRedemption = coinRedemption
        this.fiatAmount = coinAmount.toBigInteger().toUSDAmount()
        this.coinAmount = coinAmount
        this.status = TxStatus.QUEUED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: UUID, newStatus: TxStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() = find { CBT.status eq TxStatus.PENDING }

    fun findPendingForUpdate(uuid: UUID) = find { (CBT.id eq uuid) and (CBT.status eq TxStatus.PENDING) }.forUpdate()
}

class CoinBurnRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CBT, uuid) {
    companion object : CoinBurnEntityClass()

    var coinAmount by CBT.coinAmount
    var fiatAmount by CBT.fiatAmount
    var coinRedemption by CoinRedemptionRecord optionalReferencedOn CBT.coinRedemption
}
