package io.provenance.usdf.consortium.stream

data class Result(
    val query: String?,
    val data: ResultData
)

data class ResultData(
    val type: String,
    val value: ResultValue
)

data class ResultValue(
    val block: Block,
)

data class Block(
    val header: BlockHeader,
    val data: BlockData
)

data class BlockHeader(
    val height: Long
)

data class BlockData(
    val txs: List<String>?
)

data class Event(
    val type: String,
    val attributes: List<Attribute>
)

data class EventBatch(
    val height: Long,
    val events: List<StreamEvent>
)

data class StreamEvent(
    val height: Long,
    val eventType: String,
    val attributes: List<Attribute>,
    val resultIndex: Int,
    val txHash: String
)

class Attribute(
    key: ByteArray,
    value: ByteArray?
) {
    val key = String(key)
    val value = String(value ?: "".toByteArray())

    override fun toString(): String {
        return "Attribute(key='$key', value='$value')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Attribute

        if (key != other.key) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

class Subscribe(
    query: String
) : RpcRequest("subscribe", SubscribeParams(query))

open class RpcRequest(val method: String, val params: Any? = null) {
    val jsonrpc = "2.0"
    val id = "0"
}

data class RpcResponse<T>(
    val jsonrpc: String,
    val id: String,
    val result: T? = null,
    val error: RpcError? = null
)

data class RpcError(
    val code: Int,
    val message: String,
    val data: String
)

data class SubscribeParams(
    val query: String
)
