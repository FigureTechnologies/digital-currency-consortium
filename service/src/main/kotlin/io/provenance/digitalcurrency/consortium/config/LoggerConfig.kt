package io.provenance.digitalcurrency.consortium.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <T : Any> T.logger(): Logger = LoggerFactory.getLogger(this::class.java)

fun <T> withMdc(vararg items: Pair<String, Any?>, fn: () -> T): T {
    val map = items.toMap()
    try {
        map.forEach { org.slf4j.MDC.put(it.key, it.value?.toString()) }
        return fn()
    } finally {
        map.forEach { org.slf4j.MDC.remove(it.key) }
    }
}
