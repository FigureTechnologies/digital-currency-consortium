package io.provenance.digitalcurrency.consortium.extension

import com.google.protobuf.ByteString
import io.provenance.attribute.v1.AttributeType
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.attribute.v1.MsgDeleteAttributeRequest
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.messages.AmountRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteRequest
import io.provenance.digitalcurrency.consortium.messages.MintRequest
import io.provenance.digitalcurrency.consortium.messages.TransferRequest
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

// convert coins to USD (100 coins == $1.00 USD)
fun BigInteger.toUSDAmount(): BigDecimal =
    this.toBigDecimal().divide(100.toBigDecimal(), 2, RoundingMode.UNNECESSARY)

fun Long.toUSDAmount(): BigDecimal = toBigInteger().toUSDAmount()
fun String.toUSDAmount(): BigDecimal = toBigInteger().toUSDAmount()

// convert USD amount to coins (100 coins == $1.00 USD)
fun BigDecimal.toCoinAmount(): BigInteger =
    (this * 100.toBigDecimal()).toBigInteger()

fun CoinMintRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Mint/Swap",
    "status" to status,
    "coinAmount" to coinAmount,
).toTypedArray()

fun MarkerTransferRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Transfer In",
    "status" to status,
    "coinAmount" to coinAmount,
    "denom" to denom,
    "from" to fromAddress,
).toTypedArray()

fun CoinBurnRecord.mdc() = listOf(
    "uuid" to id.value,
    "type" to "Burn",
    "status" to status,
    "coinAmount" to coinAmount,
).toTypedArray()

fun CoinMintRecord.getExecuteContractMessage() =
    ExecuteRequest(
        mint = MintRequest(
            amount = coinAmount.toString(),
            address = address,
        ),
    )

fun CoinBurnRecord.getExecuteContractMessage() =
    ExecuteRequest(
        burn = AmountRequest(
            amount = coinAmount.toString(),
        ),
    )

fun CoinTransferRecord.getExecuteContractMessage() =
    ExecuteRequest(
        transfer = TransferRequest(
            recipient = address,
            amount = coinAmount.toString(),
        ),
    )

fun AddressRegistrationRecord.getAddAttributeMessage(
    managerAddress: String,
    tag: String,
) = MsgAddAttributeRequest.newBuilder()
    .setOwner(managerAddress)
    .setAccount(address)
    .setAttributeType(AttributeType.ATTRIBUTE_TYPE_BYTES)
    .setName(tag)
    .setValue(ByteString.copyFrom(bankAccountUuid.toByteArray()))
    .build()

fun AddressDeregistrationRecord.getDeleteAttributeMessage(
    managerAddress: String,
    tag: String,
) = MsgDeleteAttributeRequest.newBuilder()
    .setOwner(managerAddress)
    .setAccount(addressRegistration.address)
    .setName(tag)
    .build()
