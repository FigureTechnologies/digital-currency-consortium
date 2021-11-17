package io.provenance.digitalcurrency.consortium.frameworks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.annotation.PreDestroy
import kotlin.coroutines.CoroutineContext

/**
 * Enforces the existence of an id, for example, an app ID. Extend with fields for your use case.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class BatchDirective {
    abstract val ids: List<UUID>
}

/**
 * Enforces the existence of an id, for example, an app ID. Extend with fields for your use case.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class BatchOutcome {
    abstract val ids: List<UUID>
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
interface BatchActorModel<V : BatchDirective, R : BatchOutcome> : CoroutineScope {
    /**
     * Define number of worker co-routines to handle messages submitted to the channel.
     */
    val numWorkers: Int

    /**
     * the max number of records to include in a batch. These will be blockchain calls
     * so this is an art right now, not a science
     */
    val batchSize: Int

    /**
     * Delay between [loadMessages] calls
     */
    val pollingDelayMillis: Long

    /**
     * By default if a co-routine throws an exception, other co-routines in scope are canceled due
     * to Kotlin's structured concurrency model.
     *
     * By adding the supervisor job to the context, we ensure that an error in one worker will not affect other workers.
     * Cancellation of the supervisor job will cancel all co-routines workers in scope.
     */
    val supervisorJob: CompletableJob
        get() = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    /**
     * The class implementing this interface must call [start] to activate the actor.
     */
    fun start() = launch {
        val messageChannel = Channel<V>()
        repeat(numWorkers) { workerIndex ->
            launchWorker(workerIndex, messageChannel)
        }
        launchMsgReceiver(messageChannel)
    }

    /**
     * Cancel all co-routines in scope on shutdown.
     */
    @PreDestroy
    fun stop() {
        log().info("Batch Actor co-routines shutting down.")
        supervisorJob.cancel()
    }

    /**
     * Implementer of this interface is responsible for ensuring a message is not loaded more (duplicate processing)
     * or less often (missed processing) than intended
     *
     * Messages could come from a database, a data structure such as a queue or list, etc.
     */
    suspend fun loadMessages(): V

    /**
     * Primary processing occurs here. For example, a long running network request
     */
    fun processMessages(messages: V): R

    /**
     * Post process action upon success of [processMessages].
     */
    fun onMessagesSuccess(result: R)

    /**
     * Post process action upon failure of [processMessages].
     */
    fun onMessagesFailure(messages: V, e: Exception)

    private fun log() = LoggerFactory.getLogger(javaClass)

    private fun CoroutineScope.launchMsgReceiver(channel: SendChannel<V>) = launch {
        repeatUntilCancelled {
            loadMessages().let {
                channel.send(it)
            }
            delay(pollingDelayMillis)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun CoroutineScope.launchWorker(workerIndex: Int, channel: ReceiveChannel<V>) = launch {
        repeatUntilCancelled {
            for (msgs in channel) {
                try {
                    val result = processMessages(msgs)
                    onMessagesSuccess(result)
                } catch (e: Exception) {
                    onMessagesFailure(msgs, e)
                }
            }
        }
    }

    private suspend fun CoroutineScope.repeatUntilCancelled(block: suspend () -> Unit) {
        while (isActive) {
            try {
                block()
                yield()
            } catch (ex: CancellationException) {
                log().info("Coroutine cancelled")
            } catch (ex: Exception) {
                log().info("Failed with {$ex}.")
            }
        }

        log().info("Coroutine exiting")
    }
}
