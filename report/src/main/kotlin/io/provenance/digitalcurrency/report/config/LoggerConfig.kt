package io.provenance.digitalcurrency.report.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <T : Any> T.logger(): Logger = LoggerFactory.getLogger(this::class.java)
fun logger(name: String): Logger = LoggerFactory.getLogger(name)
