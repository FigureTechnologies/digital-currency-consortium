package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.util.UUID

typealias CBT = CoinBurnTable

object CoinBurnTable : BaseCoinRequestTable(name = "coin_burn_v2")

open class CoinRedeemBurnEntityClass : BaseCoinRequestEntityClass<CBT, CoinBurnRecord>(CBT) {

    fun findPending() = find { CBT.status inList listOf(TxStatus.PENDING, TxStatus.QUEUED) }

    fun findPendingAmount() = findPending()
        .fold(0L) { acc, record -> acc + record.coinAmount }
        .toBigInteger()

    fun findTxnCompleted() = find { CBT.status eq TxStatus.TXN_COMPLETE }

    fun findTxnCompletedForUpdate(uuid: UUID) = find { (CBT.id eq uuid) and (CBT.status eq TxStatus.TXN_COMPLETE) }.forUpdate()
}

class CoinBurnRecord(uuid: EntityID<UUID>) : BaseCoinRequestRecord(CBT, uuid) {
    companion object : CoinRedeemBurnEntityClass()
}
