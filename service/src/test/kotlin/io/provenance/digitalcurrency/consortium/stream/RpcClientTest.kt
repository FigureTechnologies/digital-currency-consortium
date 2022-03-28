package io.provenance.digitalcurrency.consortium.stream

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockMeta
import io.provenance.digitalcurrency.consortium.pbclient.block
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class RpcClientTest {

    private val RPC_URI = "http://localhost:26657"

    private val minHeight: Long = 7028100
    private val maxHeight: Long = 7028121

    val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .registerModule(ProtobufModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)

    val rpcClient: RpcClient = RpcClient.Builder(RPC_URI, objectMapper).build()

    @Test
    fun `blockchainInfo request works`() {
        val blockMetas: List<BlockMeta> =
            rpcClient.blockchainInfo(minHeight, maxHeight).result!!.blockMetas
        assertEquals(blockMetas.minOfOrNull { it.header.height }, 7028102L)
        assertEquals(blockMetas.maxOfOrNull { it.header.height }, 7028121L)
        assertEquals(blockMetas.count { it.numTxs > 0 }, 2)
    }

    @Test
    fun `block params works`() {
        val height = 7028117
        assertEquals(rpcClient.block(height).result!!.block.header.height, height.toLong())
    }

    @Test
    fun `blockResults params works`() {
        val height = 7028117
        val result = rpcClient.blockResults(7028117).result!!
        assertEquals(result.height, height.toLong())
        assertEquals(result.txsResults!!.size, 1)
    }

    @Test
    fun `abciInfo works`() {
        assertNotNull(rpcClient.abciInfo().result)
    }
}
