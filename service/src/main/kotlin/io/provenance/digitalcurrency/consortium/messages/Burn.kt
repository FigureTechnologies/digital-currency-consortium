package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteBurnRequest(
    val burn: BurnRequest? = null
)

data class BurnRequest(
    val amount: String
)
