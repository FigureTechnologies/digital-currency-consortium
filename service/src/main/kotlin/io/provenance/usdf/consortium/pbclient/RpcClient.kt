package io.provenance.usdf.consortium.pbclient

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.RequestLine
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.provenance.usdf.consortium.pbclient.api.rpc.AbciInfoMetaResponse
import io.provenance.usdf.consortium.pbclient.api.rpc.AbciInfoResponse
import io.provenance.usdf.consortium.pbclient.api.rpc.BlockRequest
import io.provenance.usdf.consortium.pbclient.api.rpc.BlockResponse
import io.provenance.usdf.consortium.pbclient.api.rpc.BlockResultsRequest
import io.provenance.usdf.consortium.pbclient.api.rpc.BlockResultsResponse
import io.provenance.usdf.consortium.pbclient.api.rpc.BlockchainInfoRequest
import io.provenance.usdf.consortium.pbclient.api.rpc.BlockchainInfoResponse
import io.provenance.usdf.consortium.stream.RpcRequest
import io.provenance.usdf.consortium.stream.RpcResponse

interface RpcClient {
    @RequestLine("GET /")
    fun blockchainInfo(request: BlockchainInfoRequest): RpcResponse<BlockchainInfoResponse>

    @RequestLine("GET /")
    fun block(request: BlockRequest): RpcResponse<BlockResponse>

    @RequestLine("GET /")
    fun blockResults(request: BlockResultsRequest): RpcResponse<BlockResultsResponse>

    @RequestLine("GET /")
    fun abciInfo(request: RpcRequest = RpcRequest("abci_info")): RpcResponse<AbciInfoMetaResponse>

    class Builder(
        private val url: String,
        private val objectMapper: ObjectMapper
    ) {
        fun build(): RpcClient = Feign.builder()
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .target(RpcClient::class.java, url)
    }
}

fun RpcClient.fetchBlocksWithTransactions(minHeight: Long, maxHeight: Long): List<Long> =
    fetchBlockchainInfo(minHeight, maxHeight).blockMetas.filter { it.numTxs > 0 }.map { it.header.height }

fun RpcClient.fetchBlockchainInfo(minHeight: Long, maxHeight: Long): BlockchainInfoResponse =
    blockchainInfo(BlockchainInfoRequest(minHeight, maxHeight)).result!!

fun RpcClient.fetchBlock(height: Long): BlockResponse = block(BlockRequest(height)).result!!

fun RpcClient.fetchBlockResults(height: Long): BlockResultsResponse = blockResults(BlockResultsRequest(height)).result!!

fun RpcClient.fetchAbciInfo(): AbciInfoResponse = abciInfo().result!!.response
