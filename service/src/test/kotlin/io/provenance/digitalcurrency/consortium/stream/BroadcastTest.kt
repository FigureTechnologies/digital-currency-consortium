package io.provenance.digitalcurrency.consortium.stream

import com.fasterxml.jackson.databind.ObjectMapper
import cosmwasm.wasm.v1.Tx.MsgExecuteContract
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimationMethod.MSG_FEE_CALCULATION
import io.provenance.client.grpc.PbClient
import io.provenance.client.wallet.fromMnemonic
import io.provenance.digitalcurrency.consortium.extension.throwIfFailed
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.extension.toTxBody
import io.provenance.digitalcurrency.consortium.messages.ExecuteRequest
import io.provenance.digitalcurrency.consortium.messages.TransferRequest
import io.provenance.digitalcurrency.consortium.service.PbcService.NetworkType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

@Disabled
class BroadcastTest {

    private val contractAddress = "tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8"
    private val user1Address = "tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p"
    private val bank3Address = "tp1zl388azlallp5rygath0kmpz6w2agpampukfc3"
    private val user3Signer = fromMnemonic(
        prefix = NetworkType.TESTNET.prefix,
        path = NetworkType.TESTNET.path,
        mnemonic = "oyster borrow survey cake puzzle trash train isolate spy this average bacon spare health toast girl regular muffin calm rain forget throw exit ring"
    )

    private val mapper = ObjectMapper()
    private val grpcClient = PbClient(
        chainId = "chain-local",
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = MSG_FEE_CALCULATION
    )

    @Test
    fun `test multiple transfers in single broadcast`() {
        grpcClient.estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(user3Signer)),
            txBody = listOf(
                ExecuteRequest(
                    transfer = TransferRequest(
                        amount = "350",
                        recipient = bank3Address
                    )
                ),
                ExecuteRequest(
                    transfer = TransferRequest(
                        amount = "100",
                        recipient = user1Address
                    )
                ),
                ExecuteRequest(
                    transfer = TransferRequest(
                        amount = "500",
                        recipient = bank3Address
                    )
                )
            )
                .map { message ->
                    MsgExecuteContract.newBuilder()
                        .setSender(user3Signer.address())
                        .setContract(contractAddress)
                        .setMsg(mapper.writeValueAsString(message).toByteString()).build()
                        .toAny()
                }
                .toTxBody(),
        ).throwIfFailed("Batch broadcast failed").also { println(it) }
    }
}
