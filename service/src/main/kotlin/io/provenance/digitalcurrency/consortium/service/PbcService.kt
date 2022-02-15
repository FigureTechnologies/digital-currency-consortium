package io.provenance.digitalcurrency.consortium.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.GeneratedMessageV3
import cosmos.authz.v1beta1.Authz.Grant
import cosmos.authz.v1beta1.Tx.MsgGrant
import cosmos.base.v1beta1.CoinOuterClass.Coin
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmwasm.wasm.v1.Tx
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import io.provenance.attribute.v1.Attribute
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getAccountCoins
import io.provenance.client.protobuf.extensions.getAllAttributes
import io.provenance.client.protobuf.extensions.getMarkerEscrow
import io.provenance.client.protobuf.extensions.getTx
import io.provenance.client.wallet.fromMnemonic
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.throwIfFailed
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.extension.toProtoTimestamp
import io.provenance.digitalcurrency.consortium.extension.toTxBody
import io.provenance.digitalcurrency.consortium.messages.AcceptRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteRequest
import io.provenance.digitalcurrency.consortium.messages.JoinRequest
import io.provenance.marker.v1.MarkerTransferAuthorization
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.OffsetDateTime
import javax.annotation.PreDestroy

@Service
class PbcService(
    private val pbClient: PbClient,
    private val provenanceProperties: ProvenanceProperties,
    private val mapper: ObjectMapper,
    bankClientProperties: BankClientProperties,
    serviceProperties: ServiceProperties,
) {
    private val log = logger()

    enum class NetworkType(
        val prefix: String,
        val path: String
    ) {
        TESTNET_HARDENED("tp", "m/44'/1'/0'/0/0'"),
        TESTNET("tp", "m/44'/1'/0'/0/0"),
        MAINNET_HARDENED("pb", "m/505'/1'/0'/0/0'"),
        MAINNET("pb", "m/505'/1'/0'/0/0")
    }

    private val managerSigner =
        when {
            provenanceProperties.mainNet && serviceProperties.managerKeyHarden -> NetworkType.MAINNET_HARDENED
            provenanceProperties.mainNet -> NetworkType.MAINNET
            serviceProperties.managerKeyHarden -> NetworkType.TESTNET_HARDENED
            else -> NetworkType.TESTNET
        }.let { networkType ->
            fromMnemonic(
                prefix = networkType.prefix,
                path = networkType.path,
                mnemonic = serviceProperties.managerKey,
                isMainNet = provenanceProperties.mainNet
            )
        }
    final val managerAddress: String by lazy { managerSigner.address() }
    final val reserveDenom = bankClientProperties.denom

    init {
        log.info("manager address $managerAddress for contract address ${provenanceProperties.contractAddress}")
    }

    fun getMarkerEscrowBalance(escrowDenom: String = reserveDenom) =
        // id is marker denom, denom is escrow denom
        pbClient.markerClient.getMarkerEscrow(reserveDenom, escrowDenom)?.amount ?: "0"

    fun getCoinBalance(address: String = managerAddress, denom: String) =
        pbClient.bankClient.getAccountCoins(address)
            .firstOrNull { it.denom == denom }
            ?.amount
            ?: "0"

    fun getTransaction(txHash: String): GetTxResponse? =
        try {
            pbClient.cosmosService.getTx(txHash)
        } catch (e: StatusRuntimeException) {
            if (listOf(Code.UNKNOWN, Code.NOT_FOUND).contains(e.status.code)) null else throw e
        }

    fun getAttributes(address: String): List<Attribute> =
        pbClient.attributeClient.getAllAttributes(address)

    fun getAttributeByTagName(address: String, tag: String): Attribute? =
        getAttributes(address).find { it.name == tag }

    fun broadcastBatch(messages: List<GeneratedMessageV3>, timeoutHeight: Long) =
        pbClient.estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerSigner)),
            txBody = messages
                .map { it.toAny() }
                .toTxBody(timeoutHeight),
            gasAdjustment = 1.3f,
        ).throwIfFailed("Batch broadcast failed")

    fun join(name: String, maxSupply: BigInteger) =
        pbClient.estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerSigner)),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteRequest(
                            join = JoinRequest(
                                denom = reserveDenom,
                                maxSupply = maxSupply.toString(),
                                name = name
                            )
                        )
                    ).toByteString()
                )
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Join failed")

    fun accept() =
        pbClient.estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerSigner)),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteRequest(
                            accept = AcceptRequest()
                        )
                    ).toByteString()
                )
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Accept failed")

    fun grantAuthz(coins: List<Coin>, expiration: OffsetDateTime?) =
        pbClient.estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerSigner)),
            txBody = MsgGrant.newBuilder()
                .setGranter(managerAddress)
                .setGrantee(provenanceProperties.contractAddress)
                .setGrant(
                    Grant.newBuilder()
                        .setExpiration((expiration ?: OffsetDateTime.now().plusYears(10)).toProtoTimestamp())
                        .setAuthorization(
                            MarkerTransferAuthorization.newBuilder()
                                .addAllTransferLimit(coins)
                                .build()
                                .toAny()
                        )
                )
                .build()
                .toAny()
                .toTxBody(),
            mode = BROADCAST_MODE_BLOCK,
            gasAdjustment = 1.5f
        ).throwIfFailed("Marker transfer authorization grant authz failed")

    @PreDestroy
    fun destroy() {
        pbClient.close()
    }
}
