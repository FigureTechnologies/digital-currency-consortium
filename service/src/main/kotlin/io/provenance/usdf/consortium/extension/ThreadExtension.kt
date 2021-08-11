package io.provenance.usdf.consortium.extension

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

fun newFixedThreadPool(size: Int, namingPattern: String): ExecutorService {
    return Executors.newFixedThreadPool(size, ThreadFactoryBuilder().setNameFormat(namingPattern).build())
        .apply {
            Runtime.getRuntime().addShutdownHook(
                thread(start = false) {
                    shutdown()
                }
            )
        }
}

fun shutdownHook(fn: () -> Unit): Thread {
    return thread(start = false, block = fn).also {
        Runtime.getRuntime().addShutdownHook(it)
    }
}

fun removeShutdownHook(hook: Thread) {
    Runtime.getRuntime().removeShutdownHook(hook)
}

fun <T, K> Collection<T>.threadedMap(executor: ExecutorService, fn: (T) -> K): Collection<K> =
    this.map { item ->
        CompletableFuture<K>().also { future ->
            thread(start = false) {
                try {
                    future.complete(fn(item))
                } catch (t: Throwable) {
                    future.completeExceptionally(t)
                }
            }.let(executor::submit)
        }
    }.map { it.get() }
