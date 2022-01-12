// ktlint-disable filename
package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteRequest(
    val join: JoinRequest? = null,
    val accept: AcceptRequest? = null,
    val burn: AmountRequest? = null,
    val mint: MintRequest? = null,
    @JsonProperty("redeem_and_burn") val redeemAndBurn: AmountRequest? = null,
    val transfer: TransferRequest? = null,
) : ContractMessageI
