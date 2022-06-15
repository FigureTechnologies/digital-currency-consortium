package io.provenance.digitalcurrency.consortium.service

import com.google.protobuf.GeneratedMessageV3
import cosmos.authz.v1beta1.Authz.Grant
import cosmos.authz.v1beta1.Tx.MsgGrant
import cosmos.base.v1beta1.CoinOuterClass.Coin
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmwasm.wasm.v1.QueryOuterClass.QuerySmartContractStateRequest
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import io.provenance.attribute.v1.Attribute
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getAccountCoins
import io.provenance.client.protobuf.extensions.getAllAttributes
import io.provenance.client.protobuf.extensions.getTx
import io.provenance.client.protobuf.extensions.queryWasm
import io.provenance.client.wallet.fromMnemonic
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.throwIfFailed
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.extension.toProtoTimestamp
import io.provenance.digitalcurrency.consortium.extension.toTxBody
import io.provenance.digitalcurrency.consortium.messages.EmptyObject
import io.provenance.digitalcurrency.consortium.messages.MemberListResponse
import io.provenance.digitalcurrency.consortium.messages.QueryRequest
import io.provenance.digitalcurrency.consortium.messages.toByteString
import io.provenance.digitalcurrency.consortium.messages.toValueResponse
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.marker.v1.MarkerTransferAuthorization
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import javax.annotation.PreDestroy

@Service
class PbcService(
    private val pbClient: PbClient,
    private val provenanceProperties: ProvenanceProperties,
    private val tendermintServiceOpenApiClient: TendermintServiceOpenApiClient,
    serviceProperties: ServiceProperties,
) {
    private val log = logger()

    enum class NetworkType(
        val prefix: String,
        val path: String
    ) {
        TESTNET_HARDENED("tp", "m/44'/1'/0'/0/0'"),
        TESTNET("tp", "m/44'/1'/0'/0/0"),
        MAINNET_HARDENED("pb", "m/44'/505'/0'/0/0'"),
        MAINNET("pb", "m/44'/505'/0'/0/0")
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

    init {
        log.info("manager address $managerAddress for contract address ${provenanceProperties.contractAddress}")
    }

    fun getBlock(blockHeight: Long): BlockResponse = runBlocking { tendermintServiceOpenApiClient.block(blockHeight) }

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
            gasAdjustment = provenanceProperties.gasAdjustment,
        ).throwIfFailed("Batch broadcast failed")

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
            gasAdjustment = provenanceProperties.gasAdjustment * 1.1
        ).throwIfFailed("Marker transfer authorization grant authz failed")

    fun getMembers(): MemberListResponse =
        pbClient.wasmClient
            .queryWasm(
                QuerySmartContractStateRequest.newBuilder()
                    .setAddress(provenanceProperties.contractAddress)
                    .setQueryData(QueryRequest(getMembers = EmptyObject()).toByteString())
                    .build()
            )
            .toValueResponse(MemberListResponse::class)

    @PreDestroy
    fun destroy() {
        pbClient.close()
    }
}
