package io.provenance.digitalcurrency.consortium.service

import io.provenance.client.PbClient
import io.provenance.client.grpc.extensions.getCurrentBlockHeight
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class PbcTimeoutService(private val pbClient: PbClient) {
    private val numberOfBlocksBeforeTimeout = 60L

    private var currentBlockHeight = 0L
    private var lastBlockLookupTime = OffsetDateTime.MIN
    private val secondsBeforeBlockHeightRefresh = 60L

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
