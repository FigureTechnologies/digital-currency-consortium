package io.provenance.digitalcurrency.consortium.stream

import org.slf4j.Logger
import java.util.concurrent.TimeUnit

fun <T> handleStream(responseObserver: EventStreamResponseObserver<T>, log: Logger) {
    while (true) {
        val isComplete = responseObserver.finishLatch.await(60, TimeUnit.SECONDS)

        when (Pair(isComplete, responseObserver.error)) {
            Pair(false, null) -> log.info("${log.name} stream active ping")
            Pair(true, null) -> {
                log.warn("${log.name} Received completed"); return
            }
            else -> throw responseObserver.error!!
        }
    }
}
