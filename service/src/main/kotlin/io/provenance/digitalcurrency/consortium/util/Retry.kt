package io.provenance.digitalcurrency.consortium.util

import io.provenance.digitalcurrency.consortium.config.logger

private val log by lazy { logger("retry") }

fun <T> retry(
    times: Int = 30,
    initialDelay: Long = 1000, // 1 seconds
    maxDelay: Long = 60000, // 1 hour
    factor: Double = 2.0,
    block: () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            log.error("Failed (current delay $currentDelay): going to retry", e)
        }
        Thread.sleep(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // last attempt
}
