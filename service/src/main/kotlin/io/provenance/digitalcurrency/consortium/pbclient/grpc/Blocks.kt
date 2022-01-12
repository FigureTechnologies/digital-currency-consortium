package io.provenance.digitalcurrency.consortium.pbclient.grpc

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import io.grpc.ManagedChannel

class Blocks(channel: ManagedChannel) {
    private val tmClient = ServiceGrpc.newBlockingStub(channel)

    fun getCurrentBlockHeight(): Long =
        tmClient.getLatestBlock(Query.GetLatestBlockRequest.getDefaultInstance()).block.header.height
}
