package io.provenance.digitalcurrency.consortium.extension

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import java.nio.ByteBuffer
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

fun Message.toAny(typeUrlPrefix: String = ""): Any = Any.pack(this, typeUrlPrefix)

fun Iterable<Any>.toTxBody(timeoutHeight: Long? = null): TxBody =
    TxBody.newBuilder()
        .addAllMessages(this)
        .also { builder -> timeoutHeight?.run { builder.timeoutHeight = this } }
        .build()

fun Any.toTxBody(timeoutHeight: Long? = null): TxBody = listOf(this).toTxBody(timeoutHeight)

fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)
fun String.toByteString(): ByteString = this.toByteArray().toByteString()

fun UUID.toByteArray(): ByteArray {
    val buffer = ByteBuffer.wrap(ByteArray(16))

    buffer.putLong(this.leastSignificantBits)
    buffer.putLong(this.mostSignificantBits)

    return buffer.array()
}

fun ByteArray.toUuid(): UUID {
    require(this.size == 16) { "ByteArray to UUID requires exactly 16 bytes" }

    val buffer = ByteBuffer.wrap(this)

    val first = buffer.long
    val second = buffer.long

    return UUID(first, second)
}

fun Timestamp.Builder.setValue(instant: Instant): Timestamp.Builder {
    this.nanos = instant.nano
    this.seconds = instant.epochSecond
    return this
}

fun Timestamp.Builder.setValue(odt: OffsetDateTime): Timestamp.Builder = setValue(odt.toInstant())

fun OffsetDateTime.toProtoTimestamp(): Timestamp = Timestamp.newBuilder().setValue(this).build()
