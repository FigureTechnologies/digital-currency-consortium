package io.provenance.usdf.consortium.pbclient

open class TransactionQueryException(message: String) : RuntimeException(message)

class TransactionNotFoundException(message: String) : TransactionQueryException(message)
