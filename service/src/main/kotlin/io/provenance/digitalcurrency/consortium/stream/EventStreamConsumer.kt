package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import io.provenance.digitalcurrency.consortium.domain.PendingTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
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
    eventStreamProperties: EventStreamProperties
) {
    private val log = logger()

    // We're only interested in transfer events from pbc
    private val eventTypes = listOf(MESSAGE_EVENT, TRANSFER_EVENT)

    // The current event stream ID
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)

    // The p8e -> pbc epoch, before which, no scopes exist on chain.
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
                handleEvents(batch.height, batch.transfers())
            }

        log.info("Starting event stream at height $lastHeight")

        eventStreamFactory.getStream(eventTypes, lastHeight + 1, responseObserver).streamEvents()

        handleStream(responseObserver, log)
    }

    protected fun handleEvents(blockHeight: Long, transfers: Transfers) {
        transfers.forEach { transfer ->
            log.info("event stream found txhash ${transfer.txHash}")
            val txStatusRecord = transaction { TxStatusRecord.findByTxHash(transfer.txHash) }
            if (transaction { txStatusRecord.empty() }) {
                if (transaction { TxStatusRecord.findByTxHash(transfer.txHash).firstOrNull() == null }
                    // TODO
                    // && transfer.recipient == pbcService.stablecoinAddress
                ) {
                    pbcService.getTransaction(transfer.txHash)
                        ?.takeIf {
                            !it.txResponse!!.isFailed()
                        }?.let {
                            // only save if it is coin sent to us
                            // sending to a diff process. Sometimes there is a race condition
                            // between the event stream and node, and the query for getting the
                            // transaction details for the hash doesn't exist yet so if we give it some time
                            // we can prevent errors/missed coin receipts
                            log.info("persist pending transfer for txhash ${transfer.txHash}")
                            transaction {
                                PendingTransferRecord.insert(
                                    txHash = transfer.txHash,
                                    blockHeight = transfer.height,
                                    amountWithDenom = transfer.amount,
                                    sender = transfer.sender,
                                    recipient = transfer.recipient
                                )
                            }
                        }
                }
            }
        }

        transaction { EventStreamRecord.update(eventStreamId, blockHeight) }
    }
}
