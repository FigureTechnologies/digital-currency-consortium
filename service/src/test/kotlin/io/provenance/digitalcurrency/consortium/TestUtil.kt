package io.provenance.digitalcurrency.consortium

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
        height = 50,
        txHash = txHash
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

fun getMintEvent(txHash: String = randomTxHash(), dccDenom: String, bankDenom: String) =
    Mint(
        amount = DEFAULT_AMOUNT.toString(),
        denom = bankDenom,
        withdrawDenom = dccDenom,
        withdrawAddress = TEST_ADDRESS,
        memberId = TEST_MEMBER_ADDRESS,
        height = 50,
        txHash = txHash
    )
