package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

typealias CRBT = CoinRedeemBurnTable

object CoinRedeemBurnTable : BaseRequestTable(name = "coin_redeem_burn") {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
}

open class CoinRedeemBurnEntityClass : BaseRequestEntityClass<CRBT, CoinRedeemBurnRecord>(CRBT) {
    fun insert(uuid: UUID, fiatAmount: BigDecimal) =
        super.insert(uuid).apply {
            this.coinAmount = fiatAmount.toCoinAmount().toLong()
            this.fiatAmount = fiatAmount
        }

    fun updateStatus(uuid: UUID, newStatus: TxStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun findTxnCompleted() = find { CRBT.status eq TxStatus.TXN_COMPLETE }

    fun findTxnCompletedForUpdate(uuid: UUID) = find { (CRBT.id eq uuid) and (CRBT.status eq TxStatus.TXN_COMPLETE) }.forUpdate()
}

class CoinRedeemBurnRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CRBT, uuid) {
    companion object : CoinRedeemBurnEntityClass()

    var coinAmount by CRBT.coinAmount
    var fiatAmount by CRBT.fiatAmount
}
