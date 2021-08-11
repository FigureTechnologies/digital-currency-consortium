package io.provenance.usdf.consortium.service

import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import io.provenance.usdf.consortium.config.ProvenanceProperties
import io.provenance.usdf.consortium.config.ServiceProperties
import io.provenance.usdf.consortium.config.logger
// import io.provenance.usdf.consortium.wallet.account.Key
// import io.provenance.usdf.consortium.wallet.account.KeyRing
import org.springframework.stereotype.Service

val TIMEOUT_EXCEPTION_MESSAGE = "timed out waiting for tx to be included in a block"

@Service
class PbcService(
    private val grpcClientService: GrpcClientService,
    serviceProperties: ServiceProperties,
    provenanceProperties: ProvenanceProperties,
) {
    private val log = logger()
    // private val keyRing: KeyRing = InMemoryKeyHolder.fromMnemonic(serviceProperties.managerKey, provenanceProperties.mainNet()).keyRing(0)
    // private val managerKey: Key = keyRing.key(0)

    // TODO - timing here can cause NPEs during first deploy
    // private val managerAddress: String by lazy { managerKey.address().value }

    fun getTransaction(txHash: String): GetTxResponse? =
        try {
            grpcClientService.new().transactions.getTx(txHash)
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Code.NOT_FOUND) null else throw e
        }

    // fun getAttributes(address: String): List<Attribute> =
    // grpcClientService.new().attributes.getAllAttributes(address)

    // fun getAttributeByName(address: String, name: String): Attribute? =
    // getAttributes(address).firstOrNull { it.name == name }

    // fun addAttribute(address: String, name: String, payload: Message) =
    // grpcClientService.new().estimateAndBroadcastTx(
    //     signers = listOf(BaseReqSigner(managerKey)),
    //     txBody = MsgAddAttributeRequest.newBuilder()
    //         .setOwner(managerKey.address().value)
    //         .setAccount(address)
    //         // TODO what should this be
    //         .setAttributeType(AttributeType.ATTRIBUTE_TYPE_BYTES)
    //         .setName(name)
    //         .setValue(/** TODO **/)
    //         .build()
    //         .toAny()
    //         .toTxBody(),
    //     mode = BROADCAST_MODE_BLOCK
    // ).also {
    //     if (it.txResponse.code > 0)
    //         throw PbcException("Unable to add attribute ${it.txResponse.code}:${it.txResponse.rawLog}")
    // }
}
