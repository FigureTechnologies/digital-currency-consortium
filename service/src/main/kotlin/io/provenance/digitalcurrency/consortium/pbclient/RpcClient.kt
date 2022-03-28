package io.provenance.digitalcurrency.consortium.pbclient

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.Logger.Level
import feign.Param
import feign.RequestLine
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.AbciInfoMetaResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.AbciInfoResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockResultsResponse
import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockchainInfoResponse
import io.provenance.digitalcurrency.consortium.stream.RpcResponse

interface RpcClient {
    @RequestLine("GET /blockchain?minHeight={minHeight}&maxHeight={maxHeight}")
    fun blockchainInfo(
        @Param("minHeight") minHeight: Long,
        @Param("maxHeight") maxHeight: Long,
    ): RpcResponse<BlockchainInfoResponse>

    @RequestLine("GET /block?height={height}")
    fun block(@Param("height") height: Long): RpcResponse<BlockResponse>

    @RequestLine("GET /block_results?height={height}")
    fun blockResults(@Param("height") height: Long): RpcResponse<BlockResultsResponse>

    @RequestLine("GET /abci_info")
    fun abciInfo(): RpcResponse<AbciInfoMetaResponse>

    class Builder(
        private val url: String,
        private val objectMapper: ObjectMapper,
        private val logLevel: String,
    ) {
        fun build(): RpcClient = Feign.builder()
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .logger(Slf4jLogger())
            .logLevel(Level.valueOf(logLevel.trim().uppercase()))
            .target(RpcClient::class.java, url)
    }
}

fun RpcClient.blockchainInfo(minHeight: Int, maxHeight: Int): RpcResponse<BlockchainInfoResponse> =
    blockchainInfo(minHeight = minHeight.toLong(), maxHeight = maxHeight.toLong())

fun RpcClient.block(height: Int): RpcResponse<BlockResponse> = block(height = height.toLong())

fun RpcClient.blockResults(height: Int): RpcResponse<BlockResultsResponse> = blockResults(height = height.toLong())

fun RpcClient.fetchBlocksWithTransactions(minHeight: Long, maxHeight: Long): List<Long> =
    fetchBlockchainInfo(minHeight, maxHeight).blockMetas.filter { it.numTxs > 0 }.map { it.header.height }

fun RpcClient.fetchBlockchainInfo(minHeight: Long, maxHeight: Long): BlockchainInfoResponse =
    blockchainInfo(minHeight = minHeight, maxHeight = maxHeight).result!!

fun RpcClient.fetchBlock(height: Long): BlockResponse = block(height = height).result!!

fun RpcClient.fetchBlockResults(height: Long): BlockResultsResponse = blockResults(height = height).result!!

fun RpcClient.fetchAbciInfo(): AbciInfoResponse = abciInfo().result!!.response
