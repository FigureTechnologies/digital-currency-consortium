package io.provenance.digitalcurrency.consortium.pbclient

import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReq
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReqSigner
import java.net.URI

data class GrpcClientOpts(
    val chainId: String,
    val channelUri: URI,
    val channelOpts: ChannelOpts = ContextClient.defaultChannelProperties
)

class GrpcClient(
    private val opts: GrpcClientOpts,
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { }
) : ContextClient(opts.channelUri, opts.channelOpts, channelConfigLambda) {

    private val log = logger()

    private fun baseRequest(
        txBody: TxBody,
        signers: List<BaseReqSigner>,
        gasAdjustment: Double? = null,
    ): BaseReq =
        signers.map {
            BaseReqSigner(
                // key = it.key,
                sequenceOffset = it.sequenceOffset,
                // account = it.account ?: accounts.getBaseAccount(it.key.address().value)
            )
        }.let {
            BaseReq(
                signers = it,
                body = txBody,
                chainId = opts.chainId,
                gasAdjustment = gasAdjustment
            )
        }

    // private fun estimateTx(baseReq: BaseReq): GasEstimate {
    //     val tx = Tx.newBuilder()
    //         .setBody(baseReq.body)
    //         .setAuthInfo(baseReq.buildAuthInfo())
    //         .build()
    //
    //     return baseReq.buildSignDocBytesList(tx.authInfo.toByteString(), tx.body.toByteString()).mapIndexed { index, signDocBytes ->
    //         baseReq.signers[index].key.sign(signDocBytes).signatureBytes
    //     }.let {
    //         tx.toBuilder().addAllSignatures(it).build()
    //     }.let { txFinal ->
    //         GasEstimate(
    //             cosmosService.simulate(SimulateRequest.newBuilder().setTx(txFinal).build()).gasInfo.gasUsed,
    //             baseReq.gasAdjustment
    //         )
    //     }
    // }

    // private fun broadcastTx(
    //     baseReq: BaseReq,
    //     gasEstimate: GasEstimate,
    //     mode: BroadcastMode = BroadcastMode.BROADCAST_MODE_SYNC
    // ): BroadcastTxResponse {
    //     val authInfoBytes = baseReq.buildAuthInfo(gasEstimate)
    //         .also { log.trace("broadcastTx(authInfo:$it") }
    //         .toByteString()
    //     val txBodyBytes = baseReq.body.toByteString()
    //
    //     val txRaw = baseReq.buildSignDocBytesList(authInfoBytes, txBodyBytes).mapIndexed { index, signDocBytes ->
    //         baseReq.signers[index].key.sign(signDocBytes).signatureBytes
    //     }.let {
    //         TxRaw.newBuilder()
    //             .setAuthInfoBytes(authInfoBytes)
    //             .setBodyBytes(txBodyBytes)
    //             .addAllSignatures(it)
    //             .build()
    //     }
    //     log.debug("broadcastTx(txBody:${baseReq.body}")
    //     log.trace("broadcastTx(txRaw:$txRaw")
    //
    //     return cosmosService.broadcastTx(BroadcastTxRequest.newBuilder().setTxBytes(txRaw.toByteString()).setMode(mode).build())
    // }

    // fun estimateAndBroadcastTx(
    //     txBody: TxBody,
    //     signers: List<BaseReqSigner>,
    //     mode: BroadcastMode = BroadcastMode.BROADCAST_MODE_SYNC,
    //     gasAdjustment: Double? = null,
    // ): BroadcastTxResponse = baseRequest(
    //     txBody = txBody,
    //     signers = signers,
    //     gasAdjustment = gasAdjustment
    // ).let { baseReq -> broadcastTx(baseReq, estimateTx(baseReq), mode) }
}
