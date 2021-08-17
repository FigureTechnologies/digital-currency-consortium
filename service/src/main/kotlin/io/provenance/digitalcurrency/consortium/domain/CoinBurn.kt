package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
import java.time.OffsetDateTime
import java.util.UUID

typealias CBT = CoinBurnTable

object CoinBurnTable : BaseRequestTable(name = "coin_burn") {
    val coinRedemption = (reference("coin_redemption_uuid", CoinRedemptionTable)).nullable()
    val status = enumerationByName("status", 15, CoinBurnStatus::class)
}

open class CoinBurnEntityClass : BaseRequestEntityClass<CBT, CoinBurnRecord>(CBT) {
    fun insert(
        coinRedemption: CoinRedemptionRecord?,
        coinAmount: Long
    ) = new(UUID.randomUUID()) {
        this.coinRedemption = coinRedemption
        this.fiatAmount = coinAmount.toBigInteger().toUSDAmount()
        this.coinAmount = coinAmount
        this.status = CoinBurnStatus.INSERTED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: UUID, newStatus: CoinBurnStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findPending() = find { CBT.status inList listOf(CoinBurnStatus.INSERTED, CoinBurnStatus.PENDING_BURN) }

    fun findForUpdate(uuid: UUID) = find { CBT.id eq uuid }.forUpdate()
}

class CoinBurnRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CBT, uuid) {
    companion object : CoinBurnEntityClass()

    var coinRedemption by CoinRedemptionRecord optionalReferencedOn CBT.coinRedemption
    var status by CBT.status
}

enum class CoinBurnStatus {
    INSERTED,
    PENDING_BURN,
    COMPLETE,
    EXCEPTION
}
