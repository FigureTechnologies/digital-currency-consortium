package io.provenance.digitalcurrency.consortium.pbclient.api.grpc

import com.google.protobuf.ByteString
import cosmos.auth.v1beta1.Auth
import cosmos.base.v1beta1.CoinOuterClass.Coin
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.signing.v1beta1.Signing.SignMode
import cosmos.tx.v1beta1.TxOuterClass.AuthInfo
import cosmos.tx.v1beta1.TxOuterClass.Fee
import cosmos.tx.v1beta1.TxOuterClass.ModeInfo
import cosmos.tx.v1beta1.TxOuterClass.ModeInfo.Single
import cosmos.tx.v1beta1.TxOuterClass.SignDoc
import cosmos.tx.v1beta1.TxOuterClass.SignerInfo
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.wallet.account.KeyI

data class BaseReqSigner(
    val key: KeyI,
    val sequenceOffset: Int = 0,
    val account: Auth.BaseAccount? = null
) {
    fun pubKeyAny() = Keys.PubKey.newBuilder().setKey(key.publicKey()).build().toAny()
}

data class BaseReq(
    val signers: List<BaseReqSigner>,
    val body: TxBody,
    val chainId: String,
    val gasAdjustment: Double? = null
) {
    companion object {
        const val DEFAULT_GAS_DENOM = "nhash"
    }

    fun buildAuthInfo(gasEstimate: GasEstimate = GasEstimate(0)): AuthInfo =
        AuthInfo.newBuilder()
            .setFee(
                Fee.newBuilder()
                    .addAllAmount(
                        listOf(
                            Coin.newBuilder()
                                .setDenom(DEFAULT_GAS_DENOM)
                                .setAmount(gasEstimate.fees.toString())
                                .build()
                        )
                    )
                    .setGasLimit(gasEstimate.limit)
            )
            .addAllSignerInfos(
                signers.map {
                    SignerInfo.newBuilder()
                        .setPublicKey(it.pubKeyAny())
                        .setModeInfo(
                            ModeInfo.newBuilder()
                                .setSingle(Single.newBuilder().setModeValue(SignMode.SIGN_MODE_DIRECT_VALUE))
                        )
                        .setSequence(it.account!!.sequence + it.sequenceOffset)
                        .build()
                }
            )
            .build()

    fun buildSignDocBytesList(authInfoBytes: ByteString, bodyBytes: ByteString): List<ByteArray> =
        signers.map {
            SignDoc.newBuilder()
                .setBodyBytes(bodyBytes)
                .setAuthInfoBytes(authInfoBytes)
                .setChainId(chainId)
                .setAccountNumber(it.account!!.accountNumber)
                .build()
                .toByteArray()
        }
}
