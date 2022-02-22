package io.provenance.digitalcurrency.consortium.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.protobuf.ByteString
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import cosmwasm.wasm.v1.QueryOuterClass.QuerySmartContractStateResponse
import io.provenance.digitalcurrency.consortium.extension.toByteString
import kotlin.reflect.KClass

class EmptyObject

interface JsonI

@JsonInclude(Include.NON_NULL)
class QueryRequest(
    @JsonProperty("get_members") val getMembers: EmptyObject? = null,
    @JsonProperty("get_join_proposals") val getJoinProposals: EmptyObject? = null
) : JsonI

internal val mapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())
    .registerModule(ProtobufModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

fun JsonI.toJsonString(): String = mapper.writeValueAsString(this)
fun JsonI.toByteString(): ByteString = toJsonString().toByteString()

fun <T : Any> QuerySmartContractStateResponse.toValueResponse(kClass: KClass<T>): T =
    mapper.readValue(data.toByteArray(), kClass.java)
