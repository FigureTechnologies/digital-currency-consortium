package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberResponse(
    val id: String,
    val supply: Long,
    @JsonProperty("max_supply") val maxSupply: Long,
    val denom: String,
    val joined: Long,
    val weight: Long,
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberListResponse(val members: List<MemberResponse>)
