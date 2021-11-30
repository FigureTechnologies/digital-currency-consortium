package io.provenance.digitalcurrency.consortium.domain

enum class TxRequestType {
    MINT,
    BURN,
    TAG,
    DETAG
}

enum class TxStatus {
    QUEUED, // waiting for blockchain transaction to be broadcast
    PENDING, // blockchain transaction has been broadcast, waiting to make sure it is done
    TXN_COMPLETE, // blockchain txn is complete - may be final status for most requests
    ACTION_COMPLETE // final status for requests that need to do another activity after blockchain status is complete
}

enum class TxType {
    TRANSFER_CONTRACT,
    MIGRATION,
}
