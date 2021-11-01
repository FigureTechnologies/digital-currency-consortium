package io.provenance.digitalcurrency.consortium.pbclient.grpc

import cosmos.feegrant.v1beta1.Feegrant.Grant
import cosmos.feegrant.v1beta1.QueryOuterClass.QueryAllowanceRequest
import io.grpc.ManagedChannel
import cosmos.feegrant.v1beta1.QueryGrpc as FeeGrantQueryGrpc

class FeeGrants(channel: ManagedChannel) {

    private val feeGrantClient = FeeGrantQueryGrpc.newBlockingStub(channel)

    fun getFeeGrant(granterAddress: String, granteeAddress: String): Grant =
        feeGrantClient.allowance(
            QueryAllowanceRequest
                .newBuilder()
                .setGranter(granterAddress)
                .setGrantee(granteeAddress)
                .build()
        ).allowance
}
