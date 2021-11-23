package io.provenance.digitalcurrency.consortium.domain

enum class TxRequestType {
    MINT,
    BURN,
    REDEEM,
    TAG,
    DETAG
}

enum class TxStatus {
    QUEUED,
    PENDING,
    COMPLETE,
    ERROR
}

enum class TxType {
    TRANSFER_CONTRACT,
    // MINT_CONTRACT,
    // REDEEM_CONTRACT,
    // BURN_CONTRACT,
    MIGRATION
}
