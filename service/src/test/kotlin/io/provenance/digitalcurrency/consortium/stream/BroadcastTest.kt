package io.provenance.digitalcurrency.consortium.stream

import com.fasterxml.jackson.databind.ObjectMapper
import cosmwasm.wasm.v1.Tx.MsgExecuteContract
import io.provenance.digitalcurrency.consortium.extension.throwIfFailed
import io.provenance.digitalcurrency.consortium.extension.toAny
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.extension.toTxBody
import io.provenance.digitalcurrency.consortium.messages.ExecuteRequest
import io.provenance.digitalcurrency.consortium.messages.TransferRequest
import io.provenance.digitalcurrency.consortium.pbclient.GrpcClient
import io.provenance.digitalcurrency.consortium.pbclient.GrpcClientOpts
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReqSigner
import io.provenance.digitalcurrency.consortium.wallet.account.InMemoryKeyHolder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

@Disabled
class BroadcastTest {

    private val contractAddress = "tp14hj2tavq8fpesdwxxcu44rty3hh90vhuz3ljwv"
    private val user1Address = "tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p"
    private val bank3Address = "tp1zl388azlallp5rygath0kmpz6w2agpampukfc3"
    private val user3Key = InMemoryKeyHolder
        .fromMnemonic(
            "oyster borrow survey cake puzzle trash train isolate spy this average bacon spare health toast girl regular muffin calm rain forget throw exit ring",
            false
        )
        .keyRing(0)
        .key(0, false)

    private val mapper = ObjectMapper()
    private val grpcClient = GrpcClient(
        GrpcClientOpts(
            chainId = "chain-local",
            channelUri = URI("http://localhost:9090")
        )
    )

    @Test
    fun `test multiple transfers in single broadcast`() {
        grpcClient.estimateAndBroadcastTx(
            signers = listOf(BaseReqSigner(key = user3Key)),
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
                        .setSender(user3Key.address())
                        .setContract(contractAddress)
                        .setMsg(mapper.writeValueAsString(message).toByteString()).build()
                        .toAny()
                }
                .toTxBody(),
        ).throwIfFailed("Batch broadcast failed").also { println(it) }
    }
}
