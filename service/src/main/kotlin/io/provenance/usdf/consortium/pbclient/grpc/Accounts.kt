package io.provenance.usdf.consortium.pbclient.grpc

import cosmos.auth.v1beta1.Auth.BaseAccount
import cosmos.auth.v1beta1.QueryOuterClass.QueryAccountRequest
import cosmos.bank.v1beta1.QueryOuterClass.QuerySupplyOfRequest
import cosmos.base.v1beta1.CoinOuterClass.Coin
import io.grpc.ManagedChannel
import cosmos.auth.v1beta1.QueryGrpc as AuthQueryGrpc
import cosmos.bank.v1beta1.QueryGrpc as BankQueryGrpc

class Accounts(channel: ManagedChannel) {

    private val authClient = AuthQueryGrpc.newBlockingStub(channel)
    private val bankClient = BankQueryGrpc.newBlockingStub(channel)

    fun getBaseAccount(bech32Address: String): BaseAccount =
        authClient.account(QueryAccountRequest.newBuilder().setAddress(bech32Address).build()).account.run {
            when {
                this.`is`(BaseAccount::class.java) -> unpack(BaseAccount::class.java)
                else -> throw IllegalArgumentException("Account type not handled:$typeUrl")
            }
        }

    /**
     * Queries the supply of a single coin.
     */
    fun getSupply(denom: String): Coin =
        bankClient.supplyOf(
            QuerySupplyOfRequest.newBuilder()
                .setDenom(denom)
                .build()
        ).amount
}
