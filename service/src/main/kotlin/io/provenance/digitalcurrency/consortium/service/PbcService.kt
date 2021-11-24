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
import io.provenance.digitalcurrency.consortium.messages.ExecuteAcceptRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteJoinRequest
import io.provenance.digitalcurrency.consortium.messages.JoinRequest
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReqSigner
import io.provenance.digitalcurrency.consortium.wallet.account.InMemoryKeyHolder
import io.provenance.digitalcurrency.consortium.wallet.account.KeyI
import io.provenance.digitalcurrency.consortium.wallet.account.KeyRing
import io.provenance.marker.v1.MarkerTransferAuthorization
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.OffsetDateTime

@Service
class PbcService(
    private val grpcClientService: GrpcClientService,
    private val provenanceProperties: ProvenanceProperties,
    private val mapper: ObjectMapper,
    private val bankClientProperties: BankClientProperties,
    serviceProperties: ServiceProperties,
) {
    private val log = logger()
    private val keyRing: KeyRing =
        InMemoryKeyHolder.fromMnemonic(serviceProperties.managerKey, provenanceProperties.mainNet).keyRing(0)
    private val managerKey: KeyI = keyRing.key(0, serviceProperties.managerKeyHarden)
    final val managerAddress: String by lazy { managerKey.address() }

    init {
        log.info("manager address $managerAddress for contract address ${provenanceProperties.contractAddress}")
    }

    fun getCoinBalance(address: String = managerAddress, denom: String) =
        grpcClientService.new().accounts.getAccountCoins(address)
            .firstOrNull { it.denom == denom }
            ?.amount
            ?: "0"

    fun getTransaction(txHash: String): GetTxResponse? =
        try {
            grpcClientService.new().transactions.getTx(txHash)
        } catch (e: StatusRuntimeException) {
            if (listOf(Code.UNKNOWN, Code.NOT_FOUND).contains(e.status.code)) null else throw e
        }

    fun getAttributes(address: String): List<Attribute> =
        grpcClientService.new().attributes.getAllAttributes(address)

    fun getAttributeByTagName(address: String, tag: String): Attribute? =
        getAttributes(address).find { it.name == tag }

    fun broadcastBatch(messages: List<GeneratedMessageV3>, timeoutHeight: Long) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(
                BaseReqSigner(
                    key = managerKey
                )
            ),
            txBody = messages
                .map { it.toAny() }
                .toTxBody(timeoutHeight)
        ).throwIfFailed("Batch broadcast failed")

    fun join(name: String, maxSupply: BigInteger) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteJoinRequest(
                            join = JoinRequest(
                                denom = bankClientProperties.denom,
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
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteAcceptRequest(
                            accept = AcceptRequest()
                        )
                    ).toByteString()
                )
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Accept failed")

    fun grantAuthz(coins: List<Coin>, expiration: OffsetDateTime?) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
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
            mode = BROADCAST_MODE_BLOCK
        ).throwIfFailed("Marker transfer authorization grant authz failed")
}
