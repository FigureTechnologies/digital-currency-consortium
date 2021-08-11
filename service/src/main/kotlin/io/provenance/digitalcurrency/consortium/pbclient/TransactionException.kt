package io.provenance.digitalcurrency.consortium.pbclient

open class TransactionQueryException(message: String) : RuntimeException(message)

class TransactionNotFoundException(message: String) : TransactionQueryException(message)
