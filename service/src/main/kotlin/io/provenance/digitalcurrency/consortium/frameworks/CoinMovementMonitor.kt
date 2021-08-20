package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.api.CoinMovementRequest
import io.provenance.digitalcurrency.consortium.api.CoinMovementRequestItem
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoinMovementProperties
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinMovementBookmarkRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.concurrent.thread

@Component
class CoinMovementMonitor(
    coinMovementProperties: CoinMovementProperties,
    eventStreamProperties: EventStreamProperties,
    val bankClient: BankClient,
) {
    private val log = logger()

    private val eventStreamId = UUID.fromString(eventStreamProperties.id)
    private val epochHeight = eventStreamProperties.epoch.toLong()
    private val pollingDelayMillis: Long = coinMovementProperties.pollingDelayMs.toLong()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start coin movement monitor framework")

        // insert an epoch height if there's no current bookmark
        transaction { CoinMovementBookmarkRecord.findById(eventStreamId) }
            ?: transaction { CoinMovementBookmarkRecord.insert(eventStreamId, epochHeight) }
        transaction { EventStreamRecord.findById(eventStreamId) }
            ?: transaction { EventStreamRecord.insert(eventStreamId, epochHeight) }

        thread(start = true, isDaemon = true, name = "CMM-1") {
            while (true) {
                try {
                    iteration()
                    Thread.sleep(pollingDelayMillis)
                } catch (t: Throwable) {
                    log.warn("Could not send batch", t)

                    // Thread.sleep(30 * 1_000L)
                    Thread.sleep(pollingDelayMillis)
                }
            }
        }
    }

    fun iteration() {
        val bookmark = transaction { CoinMovementBookmarkRecord.findById(eventStreamId) }!!.lastBlockHeight + 1
        val endBlock = transaction { EventStreamRecord.findById(eventStreamId) }!!.lastBlockHeight

        log.info("starting coin movement push with boundaries [$bookmark, $endBlock]")

        val request = transaction { CoinMovementRecord.findBatch(bookmark, endBlock) }.toOutput()

        log.debug("sending batch $request")

        if (request.record_count > 0) {
            bankClient.persistCoinMovement(request)
        }

        transaction { CoinMovementBookmarkRecord.update(eventStreamId, endBlock) }
    }
}

fun List<CoinMovementRecord>.toOutput() = CoinMovementRequest(
    record_count = this.size,
    records = this.map { coinMovement ->
        CoinMovementRequestItem(
            txid = coinMovement.txHash.value,
            from_address = coinMovement.fromAddress,
            from_address_bank_uuid = coinMovement.fromAddressBankUuid,
            to_address = coinMovement.toAddress,
            to_address_bank_uuid = coinMovement.toAddressBankUuid,
            block_height = coinMovement.blockHeight.toString(),
            timestamp = coinMovement.blockTime,
            amount = coinMovement.amount,
            denom = coinMovement.denom,
            type = coinMovement.type,
        )
    }
)
