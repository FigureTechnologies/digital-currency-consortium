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
abstract class Directive {
    abstract val id: UUID
}

/**
 * Enforces the existence of an id, for example, an app ID. Extend with fields for your use case.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Outcome {
    abstract val id: UUID
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
interface ActorModel<V : Directive, R : Outcome> : CoroutineScope {
    /**
     * Define number of worker co-routines to handle messages submitted to the channel.
     */
    val numWorkers: Int

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
     *
     * Example: @Service
     *          class MyActor : ActorModel<MyRequestType, MyOutcomeType> { init { runBlocking { start() } } }
     */
    fun start() = launch {
        val messageChannel = Channel<V>()
        repeat(numWorkers) { index ->
            launchWorker(index, messageChannel)
        }
        launchMsgReceiver(messageChannel)
    }

    /**
     * Cancel all co-routines in scope on shutdown.
     */
    @PreDestroy
    fun stop() {
        log().info("Actor co-routines shutting down.")
        supervisorJob.cancel()
    }

    /**
     * Implementer of this interface is responsible for ensuring a message is not loaded more (duplicate processing)
     * or less often (missed processing) than intended
     *
     * Messages could come from a database, a data structure such as a queue or list, etc.
     */
    suspend fun loadMessages(): List<V>

    /**
     * Primary processing occurs here. For example, a long running network request
     */
    fun processMessage(message: V): R

    /**
     * Post process action upon success of [processMessage].
     */
    fun onMessageSuccess(result: R)

    /**
     * Post process action upon failure of [processMessage].
     */
    fun onMessageFailure(message: V, e: Exception)

    private fun log() = LoggerFactory.getLogger(javaClass)

    private fun CoroutineScope.launchMsgReceiver(channel: SendChannel<V>) = launch {
        repeatUntilCancelled {
            loadMessages().forEach {
                channel.send(it)
            }
            delay(pollingDelayMillis)
        }
    }

    private fun CoroutineScope.launchWorker(index: Int, channel: ReceiveChannel<V>) = launch {
        repeatUntilCancelled {
            for (msg in channel) {
                log().info("$index worker Consuming ID - ${msg.id}")
                try {
                    val result = processMessage(msg)
                    onMessageSuccess(result)
                } catch (e: Exception) {
                    onMessageFailure(msg, e)
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
