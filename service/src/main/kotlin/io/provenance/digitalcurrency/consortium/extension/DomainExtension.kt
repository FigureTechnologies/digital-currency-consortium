package io.provenance.digitalcurrency.consortium.extension

import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import java.math.BigDecimal
import java.math.BigInteger

// convert coins to USD (100 coins == $1.00 USD)
fun BigInteger.toUSDAmount(): BigDecimal =
    this.toBigDecimal().divide(100.toBigDecimal()).setScale(2)

// convert USD amount to coins (100 coins == $1.00 USD)
fun BigDecimal.toCoinAmount(): BigInteger =
    (this * 100.toBigDecimal()).toBigInteger()

fun CoinMintRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Mint/Swap",
    "status" to status,
    "coinAmount" to coinAmount
).toTypedArray()

fun MarkerTransferRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Transfer In",
    "status" to status,
    "coinAmount" to coinAmount,
    "denom" to denom,
    "from" to fromAddress
).toTypedArray()

fun CoinRedemptionRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Redeem",
    "status" to status,
    "coin amount" to coinAmount,
    "to address" to addressRegistration.address
).toTypedArray()

fun CoinBurnRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Burn",
    "status" to status,
    "coin amount" to coinAmount,
    "redemption" to coinRedemption?.id?.value
).toTypedArray()
