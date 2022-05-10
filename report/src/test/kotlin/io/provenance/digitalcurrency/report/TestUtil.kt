package io.provenance.digitalcurrency.report

import kotlin.random.Random

private val charPool: List<Char> = ('a'..'z') + ('0'..'9')

fun randomTxHash() = (1..64)
    .map { Random.nextInt(0, charPool.size) }
    .map(charPool::get)
    .joinToString("")
