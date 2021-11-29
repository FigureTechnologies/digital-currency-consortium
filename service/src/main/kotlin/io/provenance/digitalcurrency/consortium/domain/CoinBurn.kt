package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.dao.id.EntityID
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
    ) = super.insert(UUID.randomUUID()).apply {
        this.coinRedemption = coinRedemption
        this.fiatAmount = coinAmount.toBigInteger().toUSDAmount()
        this.coinAmount = coinAmount
    }
}

class CoinBurnRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CBT, uuid) {
    companion object : CoinBurnEntityClass()

    var coinAmount by CBT.coinAmount
    var fiatAmount by CBT.fiatAmount
    var coinRedemption by CoinRedemptionRecord optionalReferencedOn CBT.coinRedemption
}
