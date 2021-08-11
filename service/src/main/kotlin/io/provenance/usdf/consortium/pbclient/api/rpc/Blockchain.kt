package io.provenance.usdf.consortium.pbclient.api.rpc

import com.fasterxml.jackson.annotation.JsonProperty
import io.provenance.usdf.consortium.stream.BlockHeader
import io.provenance.usdf.consortium.stream.RpcRequest

data class BlockchainInfoResponse(
    val lastHeight: Long,
    @JsonProperty("block_metas") val blockMetas: List<BlockMeta>
)

data class BlockMeta(
    @JsonProperty("block_id") val blockId: BlockId,
    @JsonProperty("block_size") val blockSize: Int,
    val header: BlockHeader,
    @JsonProperty("num_txs") val numTxs: Int
)

data class BlockId(
    val hash: String,
    val parts: PartSetHeader
)

data class PartSetHeader(
    val total: Int,
    val hash: String
)

class BlockchainInfoRequest(
    minHeight: Long,
    maxHeight: Long
) : RpcRequest("blockchain", BlockchainInfoParams(minHeight.toString(), maxHeight.toString()))

data class BlockchainInfoParams(
    val minHeight: String,
    val maxHeight: String
)
