package io.provenance.digitalcurrency.consortium.pbclient.api.rpc

import com.fasterxml.jackson.annotation.JsonProperty
import io.provenance.digitalcurrency.consortium.stream.Block
import io.provenance.digitalcurrency.consortium.stream.RpcRequest

class BlockRequest(
    height: Long
) : RpcRequest("block", BlockParams(height.toString()))

data class BlockParams(val height: String)

data class BlockResponse(
    @JsonProperty("block_id") val blockId: BlockId,
    val block: Block
)

class BlockResultsRequest(
    height: Long
) : RpcRequest("block_results", BlockParams(height.toString()))

data class BlockResultsResponse(
    val height: Long,
    @JsonProperty("txs_results") val txsResults: List<TxResultResponse>?
)
