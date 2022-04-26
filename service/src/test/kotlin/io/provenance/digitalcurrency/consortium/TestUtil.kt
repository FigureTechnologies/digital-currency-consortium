package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.stream.Burn
import io.provenance.digitalcurrency.consortium.stream.MarkerTransfer
import io.provenance.digitalcurrency.consortium.stream.Migration
import io.provenance.digitalcurrency.consortium.stream.Mint
import io.provenance.digitalcurrency.consortium.stream.Transfer
import java.math.BigInteger
import kotlin.random.Random

const val TEST_ADDRESS = "test-address"
const val TEST_MEMBER_ADDRESS = "test-member-address"
const val TEST_OTHER_MEMBER_ADDRESS = "test-other-member-address"
val DEFAULT_AMOUNT = BigInteger("1000")

private val charPool: List<Char> = ('a'..'z') + ('0'..'9')
fun randomTxHash() = (1..64)
    .map { Random.nextInt(0, charPool.size) }
    .map(charPool::get)
    .joinToString("")

fun getMigrationEvent(txHash: String = randomTxHash()) =
    Migration(
        codeId = "2",
        height = 50,
        txHash = txHash
    )

fun getTransferEvent(txHash: String = randomTxHash(), toAddress: String = TEST_MEMBER_ADDRESS, denom: String) =
    Transfer(
        amount = DEFAULT_AMOUNT.toString(),
        denom = denom,
        sender = TEST_ADDRESS,
        recipient = toAddress,
        fromMemberId = TEST_MEMBER_ADDRESS,
        toMemberId = TEST_MEMBER_ADDRESS,
        height = 50,
        txHash = txHash
    )

fun getBurnEvent(txHash: String = randomTxHash(), memberId: String = TEST_MEMBER_ADDRESS, denom: String) =
    Burn(
        amount = DEFAULT_AMOUNT.toString(),
        denom = denom,
        memberId = memberId,
        height = 50,
        txHash = txHash,
    )

fun getMarkerTransferEvent(txHash: String = randomTxHash(), toAddress: String = TEST_MEMBER_ADDRESS, denom: String) =
    MarkerTransfer(
        fromAddress = TEST_ADDRESS,
        toAddress = toAddress,
        amount = DEFAULT_AMOUNT.toString(),
        denom = denom,
        height = 50,
        txHash = txHash
    )

fun getMintEvent(txHash: String = randomTxHash(), dccDenom: String) =
    Mint(
        amount = DEFAULT_AMOUNT.toString(),
        withdrawDenom = dccDenom,
        withdrawAddress = TEST_ADDRESS,
        memberId = TEST_MEMBER_ADDRESS,
        height = 50,
        txHash = txHash
    )
