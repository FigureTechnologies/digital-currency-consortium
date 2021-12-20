// ktlint-disable filename
package io.provenance.digitalcurrency.consortium.messages

data class TransferRequest(
    val amount: String,
    val recipient: String
)
