package io.provenance.digitalcurrency.consortium.extension

import io.provenance.digitalcurrency.consortium.config.logger
import org.slf4j.Logger
import kotlin.math.pow

fun <T> retry(
    times: Int = 3,
    log: Logger = logger("retry-logger"),
    backoffMillis: Long = 100,
    block: () -> T
): T {
    for (i in 0 until times) {
        try {
            return block()
        } catch (e: Throwable) {
            if (i < times - 1) {
                log.info("Caught ${e.javaClass.canonicalName}, message=${e.message}. Retrying ${i + 1}/$times")
                Thread.sleep(backoffMillis * 2.0.pow(i).toLong())
            } else {
                throw e
            }
        }
    }
    error("Unexpected retry error")
}
