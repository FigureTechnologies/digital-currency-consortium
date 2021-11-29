package io.provenance.digitalcurrency.consortium.service

import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmwasm.wasm.v1.Tx.MsgExecuteContract
import feign.FeignException
import feign.Request
import feign.Request.HttpMethod.GET
import feign.RequestTemplate
import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired

class CoinRedemptionServiceTest : BaseIntegrationTest() {

    private val jsonParser = JsonFormat.parser()
        .usingTypeRegistry(
            JsonFormat.TypeRegistry.newBuilder()
                .add(MsgExecuteContract.getDescriptor())
                .add(cosmos.tx.v1beta1.TxOuterClass.Tx.getDescriptor())
                .build()
        )

    @Autowired
    private lateinit var bankClientMock: BankClient

    @Autowired
    private lateinit var pbcServiceMock: PbcService

    @BeforeEach
    fun beforeEach() {
        reset(bankClientMock)
        reset(pbcServiceMock)
    }

    @Nested
    inner class EventComplete {
        @Test
        fun `redemption incomplete does nothing`() {
            val redemption = transaction { insertCoinRedemption(TxStatus.PENDING, randomTxHash()) }

            // TODO
            // transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, never()).getTransaction(any())
        }

        @Test
        fun `redemption complete with burn`() {
            val redemption = transaction { insertCoinRedemption(TxStatus.PENDING, randomTxHash()) }

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())

            // TODO
            // transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(1, CoinBurnRecord.all().count(), "One burn record")
                val burn = CoinBurnRecord.all().first()
                assertEquals(redemption.id, burn.coinRedemption!!.id, "Coin burn must be for specified redemption")
                assertEquals(10L, burn.coinAmount, "Coin burn is for 10 coins")
                assertEquals(
                    TxStatus.TXN_COMPLETE,
                    CoinRedemptionRecord.findById(redemption.id)!!.status,
                    "Redemption status is now complete"
                )
                assertEquals(TxStatus.QUEUED, burn.status, "Coin burn is pending")
            }
        }

        @Test
        fun `redemption complete with partial bank coin partial burn`() {
            val redemption = transaction { insertCoinRedemption(TxStatus.PENDING, randomTxHash()) }

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx-multi-coin.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())

            // TODO
            // transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(1, CoinBurnRecord.all().count(), "One burn record")
                val burn = CoinBurnRecord.all().first()
                assertEquals(redemption.id, burn.coinRedemption!!.id, "Coin burn must be for specified redemption")
                assertEquals(199870L, burn.coinAmount, "Coin burn is for 199,870 coins")
                assertEquals(
                    TxStatus.TXN_COMPLETE,
                    CoinRedemptionRecord.findById(redemption.id)!!.status,
                    "Redemption status is now complete"
                )
                assertEquals(TxStatus.QUEUED, burn.status, "Coin burn is pending")
            }
        }

        @Test
        fun `redemption complete with no bank coin no burn`() {
            val redemption = transaction { insertCoinRedemption(TxStatus.PENDING, randomTxHash()) }

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx-no-coin.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())

            // TODO
            // transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(true, CoinBurnRecord.all().empty(), "No burn records")
                assertEquals(
                    TxStatus.TXN_COMPLETE,
                    CoinRedemptionRecord.findById(redemption.id)!!.status,
                    "Redemption status is now complete"
                )
            }
        }

        @Test
        fun `revert if deposit request fails`() {
            val redemption = transaction { insertCoinRedemption(TxStatus.PENDING, randomTxHash()) }

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())
            whenever(bankClientMock.depositFiat(any())).thenThrow(
                FeignException.BadRequest(
                    "",
                    Request.create(
                        GET,
                        "url",
                        emptyMap(),
                        null,
                        RequestTemplate()
                    ),
                    null,
                    emptyMap()
                )
            )

            // TODO
            // transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(true, CoinBurnRecord.all().empty(), "No burn records")
                assertEquals(
                    TxStatus.PENDING,
                    CoinRedemptionRecord.findById(redemption.id)!!.status,
                    "Redemption status is unchanged"
                )
            }
        }
    }
}
