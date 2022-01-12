// ktlint-disable filename
package io.provenance.digitalcurrency.consortium.messages

data class MintRequest(
    val amount: String,
    val address: String
)
