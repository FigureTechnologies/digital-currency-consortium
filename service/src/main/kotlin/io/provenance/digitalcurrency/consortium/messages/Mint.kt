package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteMintRequest(
    val mint: MintRequest? = null
) : ContractMessageI

data class MintRequest(
    val amount: String,
    val address: String
)
