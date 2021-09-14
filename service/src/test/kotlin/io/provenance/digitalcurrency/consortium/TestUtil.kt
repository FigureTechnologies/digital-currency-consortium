package io.provenance.digitalcurrency.consortium

import com.google.protobuf.Any
import cosmos.base.abci.v1beta1.Abci
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.digitalcurrency.consortium.stream.Burn
import io.provenance.digitalcurrency.consortium.stream.Transfer
import java.math.BigInteger
import java.time.OffsetDateTime
import kotlin.random.Random

const val TEST_ADDRESS = "test-address"
const val TEST_MEMBER_ADDRESS = "test-member-address"
val DEFAULT_AMOUNT = BigInteger("1000")

private val charPool: List<Char> = ('a'..'z') + ('0'..'9')
fun randomTxHash() = (1..64)
    .map { Random.nextInt(0, charPool.size) }
    .map(charPool::get)
    .joinToString("")

// private keyRing = InMemoryKeyHolder.genesis().second.keyRing(0)
private var index = 0

// fun generateAddress(): String = keyRing.key(index++).address().value

fun getDefaultResponse(txHash: String): ServiceOuterClass.BroadcastTxResponse =
    ServiceOuterClass.BroadcastTxResponse.newBuilder()
        .setTxResponse(
            Abci.TxResponse.newBuilder()
                .setTimestamp(OffsetDateTime.now().toString())
                .setRawLog("")
                .setTxhash(txHash)
                .setTx(Any.getDefaultInstance())
                .setCodespace("")
                .setCode(0)
                .setHeight(0)
                .build()
        ).build()

fun getDefaultTransactionResponse(txHash: String): ServiceOuterClass.GetTxResponse =
    ServiceOuterClass.GetTxResponse.newBuilder()
        .setTxResponse(
            Abci.TxResponse.newBuilder()
                .setTimestamp(OffsetDateTime.now().toString())
                .setRawLog("")
                .setTxhash(txHash)
                .setTx(Any.getDefaultInstance())
                .setCodespace("")
                .setCode(0)
                .setHeight(50)
                .build()
        ).build()

fun getErrorTransactionResponse(txHash: String): ServiceOuterClass.GetTxResponse =
    ServiceOuterClass.GetTxResponse.newBuilder()
        .setTxResponse(
            Abci.TxResponse.newBuilder()
                .setTimestamp(OffsetDateTime.now().toString())
                .setRawLog("error")
                .setTxhash(txHash)
                .setTx(Any.getDefaultInstance())
                .setCodespace("something")
                .setCode(11)
                .setHeight(1000)
                .build()
        ).build()

fun getPendingTransactionResponse(txHash: String): ServiceOuterClass.GetTxResponse =
    ServiceOuterClass.GetTxResponse.newBuilder()
        .setTxResponse(
            Abci.TxResponse.newBuilder()
                .setTimestamp(OffsetDateTime.now().toString())
                .setTxhash(txHash)
                .setTx(Any.getDefaultInstance())
                .setCode(0)
                .build()
        ).build()

fun getTransferEvent(txHash: String, toAddress: String = TEST_MEMBER_ADDRESS, denom: String) = Transfer(
    contractAddress = TEST_MEMBER_ADDRESS,
    amount = DEFAULT_AMOUNT.toString(),
    denom = denom,
    sender = TEST_ADDRESS,
    recipient = toAddress,
    height = 50,
    txHash = txHash
)

fun getBurnEvent(txHash: String, denom: String) = Burn(
    contractAddress = TEST_MEMBER_ADDRESS,
    amount = DEFAULT_AMOUNT.toString(),
    denom = denom,
    memberId = TEST_MEMBER_ADDRESS,
    height = 50,
    txHash = txHash
)
