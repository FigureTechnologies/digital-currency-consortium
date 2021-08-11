package io.provenance.usdf.consortium.extension

import java.time.Duration
import java.time.temporal.ChronoUnit

fun Int.seconds(): Duration = Duration.of(toLong(), ChronoUnit.SECONDS)
