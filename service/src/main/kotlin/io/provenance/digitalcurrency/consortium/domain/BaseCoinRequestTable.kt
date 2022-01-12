package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import org.jetbrains.exposed.dao.id.EntityID
import java.math.BigDecimal
import java.util.UUID

open class BaseCoinRequestTable(name: String) : BaseRequestTable(name = name) {
    val coinAmount = long("coin_amount")
    val fiatAmount = decimal("fiat_amount", 12, 2)
}

open class BaseCoinRequestEntityClass<T : BaseCoinRequestTable, R : BaseCoinRequestRecord>(childTable: T) :
    BaseRequestEntityClass<T, R>(childTable) {

    fun insert(uuid: UUID, fiatAmount: BigDecimal) = super.insert(uuid).apply {
        this.fiatAmount = fiatAmount
        this.coinAmount = fiatAmount.toCoinAmount().toLong()
    }
}

open class BaseCoinRequestRecord(childTable: BaseCoinRequestTable, uuid: EntityID<UUID>) : BaseRequestRecord(childTable, uuid) {
    var coinAmount by childTable.coinAmount
    var fiatAmount by childTable.fiatAmount
}
