package io.provenance.digitalcurrency.consortium.stream

import com.google.common.io.BaseEncoding
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.ShutdownReason
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.newFixedThreadPool
import io.provenance.digitalcurrency.consortium.extension.removeShutdownHook
import io.provenance.digitalcurrency.consortium.extension.shutdownHook
import io.provenance.digitalcurrency.consortium.extension.threadedMap
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.fetchAbciInfo
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlockResults
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlocksWithTransactions
import io.reactivex.disposables.Disposable
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class RpcEventStream(
    private val eventTypes: List<String>,
    private val startHeight: Long,
    private val observer: EventStreamResponseObserver<EventBatch>,
    private val lifecycle: LifecycleRegistry,
    private val eventStreamService: EventStreamService,
    private val rpcClient: RpcClient
) {
    companion object {
        private const val HISTORY_BATCH_SIZE = 10
        private val executor = newFixedThreadPool(HISTORY_BATCH_SIZE, "event-stream-%d")
    }

    private val log = logger()
    private var subscription: Disposable? = null
    private var shuttingDown = CountDownLatch(1)
    private var shutdownHook: Thread? = null
    private var lastBlockSeen = AtomicLong(-1)

    private val eventMonitor = thread(start = false, isDaemon = true) {
        var lastBlockMonitored = lastBlockSeen.get()
        while (!shuttingDown.await(30, SECONDS)) {
            val lastBlockSeen = lastBlockSeen.get()
            log.debug("Checking for event stream liveliness [lastBlockSeen: $lastBlockSeen vs. lastBlockMonitored: $lastBlockMonitored]")
            if (lastBlockSeen <= lastBlockMonitored) {
                handleError(EventStreamStaleException("EventStream has not received a block in 30 seconds [lastBlockSeen: $lastBlockSeen vs. lastBlockMonitored: $lastBlockMonitored]"))
                break
            }
            lastBlockMonitored = lastBlockSeen
        }
        log.info("Exiting event monitor thread")
    }

    fun streamEvents() {
        // start event loop to start listening for events
        try {
            startEventLoop()
            shutdownHook = shutdownHook { shutdown(false) } // ensure we close socket gracefully when shutting down

            streamHistory()

            eventMonitor.start()
        } catch (t: Throwable) {
            log.error("Error starting up EventStream: ${t.message}")
            handleError(t)
        }
    }

    private fun streamHistory() {
        if (startHeight <= 0) {
            // client only wants live events
            return
        }

        // get latest block height
        val lastBlockHeight = rpcClient.fetchAbciInfo().also { info ->
            log.info("EventStream lastBlockHeight: ${info.lastBlockHeight}")
        }.lastBlockHeight

        // Requested start height is in the future
        if (startHeight > lastBlockHeight) {
            return
        }

        // query block heights in batches (concurrent batches)
        var height = startHeight
        val capacity = HISTORY_BATCH_SIZE
        val capacityRange = (0 until capacity).toList()
        val chunkSize = 20 // Tendermint limit on how many block metas can be queried in one call.
        var numHistoricalEvents = 0
        do {
            val batches = capacityRange.threadedMap(executor) { i ->
                val beg = height + i * chunkSize
                if (beg > lastBlockHeight) {
                    null
                } else {
                    var end = beg + chunkSize - 1
                    if (end > lastBlockHeight) {
                        end = lastBlockHeight
                    }
                    queryBatchRange(beg, end)
                }
            }.filterNotNull().flatten()

            if (batches.isNotEmpty()) {
                numHistoricalEvents += batches.fold(0) { acc, batch -> acc + batch.events.size }

                batches.sortedBy { it.height }.forEach { handleEventBatch(it) }
            }

            height += capacity * chunkSize
        } while (height <= lastBlockHeight)

        log.info("Streamed $numHistoricalEvents historical events")
    }

    private fun startEventLoop() {
        // subscribe to block events tm.event='NewBlock'
        log.info("Opening EventStream websocket")
        lifecycle.onNext(Lifecycle.State.Started)

        subscription = eventStreamService.observeWebSocketEvent()
            .filter {
                if (it is WebSocket.Event.OnConnectionFailed) {
                    handleError(it.throwable)
                }

                it is WebSocket.Event.OnConnectionOpened<*>
            }
            .switchMap {
                log.info("Initializing subscription for tm.event='NewBlock'")
                eventStreamService.subscribe(Subscribe("tm.event='NewBlock'"))
                eventStreamService.streamEvents()
            }
            .filter { !it.result!!.query.isNullOrBlank() && it.result.data.value.block.header.height >= startHeight }
            .map { event -> event.result!! }
            .subscribe(
                { handleEvent(it) },
                { handleError(it) }
            )
    }

    private fun handleEvent(event: Result) {
        val blockHeight = event.data.value.block.header.height

        lastBlockSeen.set(blockHeight)

        queryEvents(blockHeight)
            .takeIf { it.isNotEmpty() }
            ?.also {
                log.info("Got batch of ${it.count()} events")
                handleEventBatch(
                    EventBatch(blockHeight, it)
                )
            }
    }

    private fun handleEventBatch(event: EventBatch) {
        observer.onNext(event)
    }

    private fun handleError(t: Throwable) {
        log.error("EventStream error ${t.message}")
        observer.onError(t)
        shutdown()
    }

    private fun shutdown(removeShutdownHook: Boolean = true) {
        log.info("Cleaning up EventStream Websocket")
        shuttingDown.countDown()
        lifecycle.onNext(Lifecycle.State.Stopped.WithReason(ShutdownReason.GRACEFUL))
        subscription
            ?.takeIf { !it.isDisposed }
            ?.dispose()
        observer.onCompleted()
        shutdownHook?.takeIf { removeShutdownHook }?.also { removeShutdownHook(it) }
    }

    private fun queryBatchRange(minHeight: Long, maxHeight: Long): List<EventBatch>? {
        if (minHeight > maxHeight) {
            return null
        }

        return rpcClient.fetchBlocksWithTransactions(minHeight, maxHeight)
            .takeIf { it.isNotEmpty() }
            ?.map { height ->
                EventBatch(
                    height,
                    queryEvents(height)
                )
            }?.filter { it.events.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun queryEvents(height: Long): List<StreamEvent> {
        val block = rpcClient.fetchBlock(height)
        if (block.block.data.txs == null || block.block.data.txs.isEmpty()) { // empty block
            return listOf()
        }

        val results = rpcClient.fetchBlockResults(height)
        return results.txsResults?.flatMapIndexed { index, tx ->
            val txHash = block.block.data.txs[index].hash()
            tx.events
                .filter { it.shouldStream(txHash) }
                .map { event ->
                    StreamEvent(
                        height = results.height,
                        eventType = event.type,
                        resultIndex = index,
                        txHash = txHash,
                        attributes = event.attributes
                    )
                }
        } ?: emptyList()
    }

    private fun Event.shouldStream(txHash: String): Boolean =
        txHash.isNotBlank() &&
            (
                eventTypes.contains(type) || // check for simple event type match first
                    eventTypes.isEmpty() || // no filtering requested
                    eventTypes.firstOrNull { // Check for "event_type:attribute_key" matches.
                    it.contains(':') && it.split(':').let { elements ->
                        elements.size == 2 &&
                            elements[0] == type && // event type match
                            attributes.firstOrNull { attribute -> attribute.key == elements[1] } != null // at least one attribute match
                    }
                } != null
                )

    private fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

    private fun ByteArray.toHexString() = BaseEncoding.base16().encode(this)

    private fun sha256(input: ByteArray?): ByteArray =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(input)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Couldn't find a SHA-256 provider", e)
        }
}
