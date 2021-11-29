package io.provenance.digitalcurrency.consortium.domain

enum class TxRequestType {
    MINT,
    BURN,
    TAG,
    DETAG
}

enum class TxStatus {
    QUEUED,
    PENDING,
    TXN_COMPLETE,
    ACTION_COMPLETE
}

enum class TxType {
    TRANSFER_CONTRACT,
    MIGRATION,
}
