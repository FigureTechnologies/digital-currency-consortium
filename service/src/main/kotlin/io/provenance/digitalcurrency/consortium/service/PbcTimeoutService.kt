package io.provenance.digitalcurrency.consortium.service

import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class PbcTimeoutService(
    private val grpcClientService: GrpcClientService
) {
    private val numberOfBlocksBeforeTimeout = 60L

    private var currentBlockHeight = 0L
    private var lastBlockLookupTime = OffsetDateTime.MIN
    private val secondsBeforeBlockHeightRefresh = 60L

    @Synchronized
    fun getBlockTimeoutHeight(): Long {
        val currentTime = OffsetDateTime.now()
        if (lastBlockLookupTime.plusSeconds(secondsBeforeBlockHeightRefresh) < currentTime) {
            lastBlockLookupTime = currentTime
            currentBlockHeight = grpcClientService.new().blocks.getCurrentBlockHeight()
        }
        return currentBlockHeight + numberOfBlocksBeforeTimeout
    }
}
