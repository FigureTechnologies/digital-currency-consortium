// ktlint-disable filename
package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteRequest(
    val burn: AmountRequest? = null,
    val mint: MintRequest? = null,
    val transfer: TransferRequest? = null,
) : ContractMessageI
