package io.provenance.digitalcurrency.consortium.service

import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getCurrentBlockHeight
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class PbcTimeoutService(private val pbClient: PbClient, provenanceProperties: ProvenanceProperties) {
    private val numberOfBlocksBeforeTimeout = provenanceProperties.blocksBeforeTimeout.toLong()

    private var currentBlockHeight = 0L
    private var lastBlockLookupTime = OffsetDateTime.MIN
    private val secondsBeforeBlockHeightRefresh = 10L

    @Synchronized
    fun getBlockTimeoutHeight(): Long {
        val currentTime = OffsetDateTime.now()
        if (lastBlockLookupTime.plusSeconds(secondsBeforeBlockHeightRefresh) < currentTime) {
            lastBlockLookupTime = currentTime
            currentBlockHeight = pbClient.tendermintService.getCurrentBlockHeight()
        }
        return currentBlockHeight + numberOfBlocksBeforeTimeout
    }
}
