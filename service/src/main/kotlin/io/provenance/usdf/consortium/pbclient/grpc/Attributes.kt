package io.provenance.usdf.consortium.pbclient.grpc

import io.grpc.ManagedChannel
import io.provenance.attribute.v1.Attribute
import io.provenance.attribute.v1.QueryAttributesRequest
import io.provenance.usdf.consortium.extension.newPaginationBuilder
import io.provenance.attribute.v1.QueryGrpc as AttributeQueryGrpc

class Attributes(channel: ManagedChannel) {

    private val attrClient = AttributeQueryGrpc.newBlockingStub(channel)

    fun getAllAttributes(address: String): List<Attribute> {
        var offset = 0
        var total: Long
        val limit = 100
        val attributes = mutableListOf<Attribute>()

        do {
            val results = attrClient
                .attributes(
                    QueryAttributesRequest.newBuilder()
                        .setAccount(address)
                        .setPagination(newPaginationBuilder(offset, limit))
                        .build()
                )
            total = results.pagination?.total ?: results.attributesCount.toLong()
            offset += limit
            attributes.addAll(results.attributesList)
        } while (attributes.count() < total)

        return attributes
    }
}
