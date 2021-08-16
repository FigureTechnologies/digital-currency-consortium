package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EventStreamConsumer(
    private val eventStreamFactory: EventStreamFactory,
    private val pbcService: PbcService,
    eventStreamProperties: EventStreamProperties,
    private val serviceProperties: ServiceProperties
) {
    private val log = logger()

    // We're only interested in transfer events from pbc
    private val eventTypes = listOf(MARKER_TRANSFER_EVENT, WITHDRAW_EVENT, BURN_EVENT)

    // The current event stream ID
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)

    private val epochHeight = eventStreamProperties.epoch.toLong()

    // This is scheduled so if the event streaming server or its proxied blockchain daemon node go down,
    // we'll attempt to re-connect after a fixed delay.
    @Scheduled(
        initialDelayString = "\${event_stream.connect.initial_delay.ms}",
        fixedDelayString = "\${event_stream.connect.delay.ms}"
    )
    fun consumeEventStream() {
        // Initialize event stream state and determine start height
        val record = transaction { EventStreamRecord.findById(eventStreamId) }
        val lastHeight = record?.lastBlockHeight
            ?: transaction { EventStreamRecord.insert(eventStreamId, epochHeight) }.lastBlockHeight
        val responseObserver =
            EventStreamResponseObserver<EventBatch> { batch ->
                handleEvents(batch.height, batch.burns(), batch.withdraws(), batch.transfers())
            }

        log.info("Starting event stream at height $lastHeight")

        eventStreamFactory.getStream(eventTypes, lastHeight + 1, responseObserver).streamEvents()

        handleStream(responseObserver, log)
    }

    protected fun handleEvents(blockHeight: Long, burns: Burns, withdraws: Withdraws, markerTransfers: MarkerTransfers) {
        val events =
            burns.map { Triple(it.txHash, TxType.MARKER_BURN, it) } +
                withdraws.map { Triple(it.txHash, TxType.MARKER_WITHDRAW, it) } +
                markerTransfers.map { Triple(it.txHash, TxType.MARKER_TRANSFER, it) }

        events.forEach { (txHash, type, event) ->
            log.info("event stream found txhash $txHash and type $type [event = {$event}]")
            val txStatusRecord = transaction { TxStatusRecord.findByTxHash(txHash) }
            if (transaction { txStatusRecord.empty() }) {
                if (event is MarkerTransfer &&
                    transaction { MarkerTransferRecord.findByTxHash(txHash) == null } &&
                    event.toAddress == pbcService.managerAddress &&
                    event.denom == serviceProperties.dccDenom
                ) {
                    pbcService.getTransaction(txHash)
                        ?.takeIf {
                            !it.txResponse!!.isFailed()
                        }?.let {
                            log.info("persist received transfer for txhash $txHash")
                            transaction {
                                MarkerTransferRecord.insert(
                                    fromAddress = event.fromAddress,
                                    toAddress = event.toAddress,
                                    denom = event.denom,
                                    coins = event.amount,
                                    height = event.height,
                                    txHash = txHash
                                )
                            }
                        }
                }
            } else {
                transaction {
                    val lockedStatusRecord = txStatusRecord.forUpdate().first()
                    when (lockedStatusRecord.status) {
                        TxStatus.COMPLETE -> log.warn("Tx status already complete uuid:${lockedStatusRecord.id.value}")
                        TxStatus.ERROR -> log.error("Tx status was already error but received a complete uuid:${lockedStatusRecord.id.value}")
                        else -> {
                            val txResponse = pbcService.getTransaction(txHash)?.txResponse
                            when {
                                txResponse == null -> {
                                    log.error("Invalid (NULL) transaction response")
                                    lockedStatusRecord.setStatus(
                                        TxStatus.ERROR,
                                        "Invalid (NULL) transaction response"
                                    )
                                }
                                txResponse.isFailed() -> {
                                    log.error("Transaction failed: $txResponse")
                                    lockedStatusRecord.setStatus(
                                        TxStatus.ERROR,
                                        txResponse.rawLog
                                    )
                                }
                                else -> lockedStatusRecord.setStatus(TxStatus.COMPLETE)
                            }
                        }
                    }
                }
            }
        }

        transaction { EventStreamRecord.update(eventStreamId, blockHeight) }
    }
}
