package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoinMovementProperties
import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinMovementBookmarkRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.EventStreamRecord
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.concurrent.thread

@Component
class CoinMovementMonitor(
    coinMovementProperties: CoinMovementProperties,
    eventStreamProperties: EventStreamProperties,
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
                    // TODO (steve) catch http responses and log response?
                    Thread.sleep(30 * 1_000L)
                }
            }
        }
    }

    fun iteration() {
        val bookmark = transaction { CoinMovementBookmarkRecord.findById(eventStreamId) }!!.lastBlockHeight + 1
        val endBlock = transaction { EventStreamRecord.findById(eventStreamId) }!!.lastBlockHeight - 1

        log.info("starting coin movement push with boundaries [$bookmark, $endBlock]")

        val batch = transaction { CoinMovementRecord.findBatch(bookmark, endBlock) }.toOutput()

        log.debug("sending batch $batch")

        // TODO (steve) send batch to nycb's endpoint

        transaction { CoinMovementBookmarkRecord.update(eventStreamId, endBlock + 1) }
    }
}

data class CoinMovementItem(
    val txid: String,
    val from_address: String,
    val from_address_bank_uuid: UUID?,
    val to_address: String,
    val to_address_bank_uuid: UUID?,
    val block_height: String,
    val timestamp: OffsetDateTime,
    val amount: String,
    val denom: String,
    val type: String,
)

data class CoinMovementList(
    val record_count: Int,
    val records: List<CoinMovementItem>,
)

fun List<CoinMovementRecord>.toOutput() = CoinMovementList(
    record_count = this.size,
    records = this.map { coinMovement ->
        CoinMovementItem(
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
