package io.provenance.digitalcurrency.consortium.pbclient.grpc

import cosmos.auth.v1beta1.Auth.BaseAccount
import cosmos.auth.v1beta1.QueryOuterClass.QueryAccountRequest
import io.grpc.ManagedChannel
import cosmos.auth.v1beta1.QueryGrpc as AuthQueryGrpc

class Accounts(channel: ManagedChannel) {

    private val authClient = AuthQueryGrpc.newBlockingStub(channel)

    fun getBaseAccount(bech32Address: String): BaseAccount =
        authClient.account(QueryAccountRequest.newBuilder().setAddress(bech32Address).build()).account.run {
            when {
                this.`is`(BaseAccount::class.java) -> unpack(BaseAccount::class.java)
                else -> throw IllegalArgumentException("Account type not handled:$typeUrl")
            }
        }
}
