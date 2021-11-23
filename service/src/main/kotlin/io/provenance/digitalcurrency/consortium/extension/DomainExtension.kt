package io.provenance.digitalcurrency.consortium.extension

import com.google.protobuf.ByteString
import io.provenance.attribute.v1.AttributeType
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.attribute.v1.MsgDeleteAttributeRequest
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.messages.BurnRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteBurnRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteMintRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteRedeemRequest
import io.provenance.digitalcurrency.consortium.messages.MintRequest
import io.provenance.digitalcurrency.consortium.messages.RedeemRequest
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

// convert coins to USD (100 coins == $1.00 USD)
fun BigInteger.toUSDAmount(): BigDecimal =
    this.toBigDecimal().divide(100.toBigDecimal(), 2, RoundingMode.UNNECESSARY)

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

fun AddressRegistrationRecord.mdc() = listOf(
    "uuid" to id.value,
    "address" to address,
    "status" to status,
    "txhash" to txHash
).toTypedArray()

fun AddressDeregistrationRecord.mdc() = listOf(
    "uuid" to id.value,
    "status" to status,
    "txhash" to txHash
).toTypedArray()

fun CoinMintRecord.getExecuteContractMessage() =
    ExecuteMintRequest(
        mint = MintRequest(
            amount = coinAmount.toString(),
            address = addressRegistration.address
        )
    )

fun CoinRedemptionRecord.getExecuteContractMessage(bankDenom: String) =
    ExecuteRedeemRequest(
        redeem = RedeemRequest(
            amount = coinAmount.toString(),
            reserveDenom = bankDenom
        )
    )

fun CoinBurnRecord.getExecuteContractMessage() =
    ExecuteBurnRequest(
        burn = BurnRequest(
            amount = coinAmount.toString(),
        )
    )

fun AddressRegistrationRecord.getAddAttributeMessage(
    managerAddress: String,
    tag: String
) = MsgAddAttributeRequest.newBuilder()
    .setOwner(managerAddress)
    .setAccount(address)
    .setAttributeType(AttributeType.ATTRIBUTE_TYPE_BYTES)
    .setName(tag)
    .setValue(ByteString.copyFrom(bankAccountUuid.toByteArray()))
    .build()

fun AddressDeregistrationRecord.getDeleteAttributeMessage(
    managerAddress: String,
    tag: String
) = MsgDeleteAttributeRequest.newBuilder()
    .setOwner(managerAddress)
    .setAccount(addressRegistration.address)
    .setName(tag)
    .build()
