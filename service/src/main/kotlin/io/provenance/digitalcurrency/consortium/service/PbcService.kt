package io.provenance.digitalcurrency.consortium.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmwasm.wasm.v1beta1.Tx
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import io.provenance.attribute.v1.Attribute
import io.provenance.attribute.v1.AttributeType
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.digitalcurrency.consortium.api.BurnRequest
import io.provenance.digitalcurrency.consortium.api.ExecuteBurnRequest
import io.provenance.digitalcurrency.consortium.api.ExecuteMintRequest
import io.provenance.digitalcurrency.consortium.api.ExecuteRedeemRequest
import io.provenance.digitalcurrency.consortium.api.MintRequest
import io.provenance.digitalcurrency.consortium.api.RedeemRequest
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.throwIfFailed
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.extension.toTxBody
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReqSigner
import io.provenance.digitalcurrency.consortium.wallet.account.InMemoryKeyHolder
import io.provenance.digitalcurrency.consortium.wallet.account.KeyI
import io.provenance.digitalcurrency.consortium.wallet.account.KeyRing
import org.springframework.stereotype.Service
import java.math.BigInteger

val TIMEOUT_EXCEPTION_MESSAGE = "timed out waiting for tx to be included in a block"

@Service
class PbcService(
    private val grpcClientService: GrpcClientService,
    serviceProperties: ServiceProperties,
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

    fun getTransaction(txHash: String): GetTxResponse? =
        try {
            grpcClientService.new().transactions.getTx(txHash)
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Code.NOT_FOUND) null else throw e
        }

    fun getAttributes(address: String): List<Attribute> =
        grpcClientService.new().attributes.getAllAttributes(address)

    fun getAttributeByName(address: String, name: String): Attribute? =
        getAttributes(address).firstOrNull { it.name == name }

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
                .toTxBody(),
            // TODO this should be sync I think - we've had timeout issues where the initial
            // error was the txn could not be included in a block, but then the tag
            // eventually worked
            mode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK
        ).throwIfFailed("Add attribute failed")

    fun mintAndSwap(amount: BigInteger, address: String) =
        grpcClientService.new().estimateAndBroadcastTx(
            signers = listOf(
                BaseReqSigner(
                    key = managerKey
                )
            ),
            txBody = Tx.MsgExecuteContract.newBuilder()
                .setSender(provenanceProperties.contractAdminAddress)
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
                .setSender(provenanceProperties.contractAdminAddress)
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
                .setSender(provenanceProperties.contractAdminAddress)
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
}
