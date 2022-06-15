package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberResponse(
    val id: String,
    val joined: Long,
    val name: String,
    @JsonProperty("kyc_attrs") val kycAttributes: List<String>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberListResponse(val members: List<MemberResponse>)
