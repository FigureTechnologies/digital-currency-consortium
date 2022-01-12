package io.provenance.digitalcurrency.consortium.pbclient.grpc

import cosmos.base.v1beta1.CoinOuterClass.Coin
import io.grpc.ManagedChannel
import io.provenance.marker.v1.QueryEscrowRequest
import io.provenance.marker.v1.QueryGrpc as MarkerQueryGrpc

class Markers(channel: ManagedChannel) {

    private val markerClient = MarkerQueryGrpc.newBlockingStub(channel)

    fun getMarkerEscrow(id: String, escrowDenom: String): Coin? =
        markerClient.escrow(QueryEscrowRequest.newBuilder().setId(id).build()).escrowList.firstOrNull { it.denom == escrowDenom }
}
