package io.provenance.digitalcurrency.report.stream

import io.provenance.digitalcurrency.report.config.ContractProperties
import io.provenance.digitalcurrency.report.config.EventStreamProperties
import io.provenance.digitalcurrency.report.config.logger
import io.provenance.digitalcurrency.report.domain.CoinMovementRecord
import io.provenance.digitalcurrency.report.domain.EventStreamRecord
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.flows.blockDataFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EventStreamConsumer(
    private val contractProperties: ContractProperties,
    private val eventStreamProperties: EventStreamProperties,
) {

    private val log = logger()
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)

    @Scheduled(
        initialDelayString = "\${event_stream.connect.initial_delay.ms}",
        fixedDelayString = "\${event_stream.connect.delay.ms}"
    )
    fun consumeEventStream() {
        // Initialize event stream state and determine start height
        val record = transaction { EventStreamRecord.findById(eventStreamId) }
        val lastHeight = record?.lastBlockHeight
            ?: transaction { EventStreamRecord.insert(eventStreamId, eventStreamProperties.fromHeight) }.lastBlockHeight

        runBlocking {
            val netAdapter = okHttpNetAdapter(eventStreamProperties.uri)

            blockDataFlow(
                netAdapter = netAdapter,
                decoderAdapter = moshiDecoderAdapter(),
                from = lastHeight
            ).collect { blockData ->
                val transfers = blockData.transfers(contractProperties.address)
                if (transfers.isNotEmpty()) {
                    log.info("Handling transfers at height:${blockData.height}")

                    transaction {
                        transfers.forEach { transfer ->
                            CoinMovementRecord.insert(
                                txHash = transfer.txHash,
                                fromAddress = transfer.sender,
                                fromMemberId = transfer.fromMemberId,
                                toAddress = transfer.recipient,
                                toMemberId = transfer.toMemberId,
                                blockHeight = transfer.height,
                                blockTime = transfer.dateTime,
                                amount = transfer.amount,
                            )
                        }
                    }
                }

                transaction {
                    EventStreamRecord.update(eventStreamId, blockData.height)
                }
            }

            netAdapter.shutdown()
        }
    }
}
