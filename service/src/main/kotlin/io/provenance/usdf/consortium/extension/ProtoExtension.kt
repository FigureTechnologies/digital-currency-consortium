package io.provenance.usdf.consortium.extension

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import cosmos.base.query.v1beta1.Pagination.PageRequest
import cosmos.tx.v1beta1.TxOuterClass.TxBody

fun newPaginationBuilder(offset: Int, limit: Int): PageRequest.Builder =
    PageRequest.newBuilder().setOffset(offset.toLong()).setLimit(limit.toLong()).setCountTotal(true)

fun Message.toAny(typeUrlPrefix: String = ""): Any = Any.pack(this, typeUrlPrefix)

fun Iterable<Any>.toTxBody(memo: String? = null): TxBody =
    TxBody.newBuilder()
        .addAllMessages(this)
        .also { builder -> memo?.run { builder.memo = this } }
        .build()

fun Any.toTxBody(memo: String? = null): TxBody = listOf(this).toTxBody(memo)

fun String.toByteString() = toByteArray().toByteString()
fun ByteArray.toByteString() = ByteString.copyFrom(this)
