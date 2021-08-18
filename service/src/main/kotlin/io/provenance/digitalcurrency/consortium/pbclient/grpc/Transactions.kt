package io.provenance.digitalcurrency.consortium.pbclient.grpc

import cosmos.tx.v1beta1.ServiceGrpc
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxRequest
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import io.grpc.ManagedChannel

class Transactions(channel: ManagedChannel) {

    private val txClient = ServiceGrpc.newBlockingStub(channel)

    fun getTx(hash: String): GetTxResponse = txClient.getTx(GetTxRequest.newBuilder().setHash(hash).build())
}
