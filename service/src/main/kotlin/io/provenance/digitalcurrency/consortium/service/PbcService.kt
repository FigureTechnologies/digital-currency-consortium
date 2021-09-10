package io.provenance.digitalcurrency.consortium.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmwasm.wasm.v1beta1.Tx
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import io.provenance.attribute.v1.Attribute
import io.provenance.attribute.v1.AttributeType
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.attribute.v1.MsgDeleteAttributeRequest
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.throwIfFailed
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.extension.toTxBody
import io.provenance.digitalcurrency.consortium.messages.AcceptRequest
import io.provenance.digitalcurrency.consortium.messages.BurnRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteAcceptRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteBurnRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteJoinRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteMintRequest
import io.provenance.digitalcurrency.consortium.messages.ExecuteRedeemRequest
import io.provenance.digitalcurrency.consortium.messages.JoinRequest
import io.provenance.digitalcurrency.consortium.messages.MintRequest
import io.provenance.digitalcurrency.consortium.messages.RedeemRequest
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReqSigner
import io.provenance.digitalcurrency.consortium.wallet.account.InMemoryKeyHolder
import io.provenance.digitalcurrency.consortium.wallet.account.KeyI
import io.provenance.digitalcurrency.consortium.wallet.account.KeyRing
import org.springframework.stereotype.Service
import java.math.BigInteger

@Service
class PbcService(
    private val grpcClientService: GrpcClientService,
    private val serviceProperties: ServiceProperties,
    private val provenanceProperties: ProvenanceProperties,
    private val mapper: ObjectMapper,
    private val bankClientProperties: BankClientProperties
) {
    private val log = logger()
    private val keyRing: KeyRing =
        InMemoryKeyHolder.fromMnemonic(serviceProperties.managerKey, provenanceProperties.mainNet()).keyRing(0)
    private val managerKey: KeyI = keyRing.key(0)
    final val managerAddress: String by lazy { managerKey.address() }

    init {
        log.info("manager address $managerAddress for contract address ${provenanceProperties.contractAddress}")
    }

    fun getCoinBalance(address: String = managerAddress, denom: String = serviceProperties.dccDenom) =
        grpcClientService.new().accounts.getAccountCoins(address)
            .firstOrNull { it.denom == denom }
            ?.amount
            ?: "0"

    fun getTransaction(txHash: String): GetTxResponse? =
        try {
            grpcClientService.new().transactions.getTx(txHash)
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Code.NOT_FOUND) null else throw e
        }

    fun getAttributes(address: String): List<Attribute> =
        grpcClientService.new().attributes.getAllAttributes(address)

    fun getAttributeByTagName(address: String, tag: String): Attribute? =
        getAttributes(address).find { it.name == tag }

    fun addAttribute(address: String, tag: String, payload: ByteString) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
            txBody = MsgAddAttributeRequest.newBuilder()
                .setOwner(managerAddress)
                .setAccount(address)
                .setAttributeType(AttributeType.ATTRIBUTE_TYPE_BYTES)
                .setName(tag)
                .setValue(payload)
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Add attribute failed")

    fun deleteAttribute(address: String, tag: String) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
            txBody = MsgDeleteAttributeRequest.newBuilder()
                .setOwner(managerAddress)
                .setAccount(address)
                .setName(tag)
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Delete attribute failed")

    fun mintAndSwap(amount: BigInteger, address: String) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(
                BaseReqSigner(
                    key = managerKey
                )
            ),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteMintRequest(
                            mint = MintRequest(
                                amount = amount.toString(),
                                address = address
                            )
                        )
                    ).toByteString()
                )
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Mint/swap failed")

    fun redeem(amount: BigInteger) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteRedeemRequest(
                            redeem = RedeemRequest(
                                amount = amount.toString(),
                                reserveDenom = bankClientProperties.denom
                            )
                        )
                    ).toByteString()
                )
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Redeem failed")

    fun burn(amount: BigInteger) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(managerKey)),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(managerAddress)
                .setContract(provenanceProperties.contractAddress)
                .setMsg(
                    mapper.writeValueAsString(
                        ExecuteBurnRequest(
                            burn = BurnRequest(
                                amount = amount.toString()
                            )
                        )
                    ).toByteString()
                )
                .build()
                .toAny()
                .toTxBody()
        ).throwIfFailed("Burn failed")

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
}
