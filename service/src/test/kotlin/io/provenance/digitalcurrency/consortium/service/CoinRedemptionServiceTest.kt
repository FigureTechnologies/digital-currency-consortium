package io.provenance.digitalcurrency.consortium.service

import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmwasm.wasm.v1.Tx.MsgExecuteContract
import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnStatus
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus.COMPLETE
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus.PENDING_REDEEM
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus.PENDING
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
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

    private val bankClientProperties = BankClientProperties("", "", "bank3.coin")

    @Autowired
    private lateinit var bankClientMock: BankClient

    @Autowired
    private lateinit var pbcServiceMock: PbcService

    private lateinit var coinRedemptionService: CoinRedemptionService

    @BeforeEach
    fun beforeEach() {
        reset(bankClientMock)
        reset(pbcServiceMock)
    }

    @BeforeAll
    fun beforeAll() {
        coinRedemptionService = CoinRedemptionService(bankClientMock, pbcServiceMock, bankClientProperties)
    }

    @Nested
    inner class EventComplete {
        @Test
        fun `redemption incomplete does nothing`() {
            val redemption = transaction { insertCoinRedemption(PENDING_REDEEM) }
            insertTxStatus(
                redemption.id.value,
                txHash = randomTxHash(),
                txType = TxType.REDEEM_CONTRACT,
                txStatus = PENDING
            )

            transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, never()).getTransaction(any())
        }

        @Test
        fun `redemption complete with burn`() {
            val redemption = transaction { insertCoinRedemption(PENDING_REDEEM) }
            insertTxStatus(
                redemption.id.value,
                txHash = randomTxHash(),
                txType = TxType.REDEEM_CONTRACT,
                txStatus = TxStatus.COMPLETE
            )

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())

            transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(1, CoinBurnRecord.all().count(), "One burn record")
                val burn = CoinBurnRecord.all().first()
                assertEquals(redemption.id, burn.coinRedemption!!.id, "Coin burn must be for specified redemption")
                assertEquals(10L, burn.coinAmount, "Coin burn is for 10 coins")
                assertEquals(COMPLETE, CoinRedemptionRecord.findById(redemption.id)!!.status, "Redemption status is now complete")
                assertEquals(CoinBurnStatus.INSERTED, burn.status, "Coin burn is pending")
            }
        }

        @Test
        fun `redemption complete with partial bank coin partial burn`() {
            val redemption = transaction { insertCoinRedemption(PENDING_REDEEM) }
            insertTxStatus(
                redemption.id.value,
                txHash = randomTxHash(),
                txType = TxType.REDEEM_CONTRACT,
                txStatus = TxStatus.COMPLETE
            )

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx-multi-coin.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())

            transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(1, CoinBurnRecord.all().count(), "One burn record")
                val burn = CoinBurnRecord.all().first()
                assertEquals(redemption.id, burn.coinRedemption!!.id, "Coin burn must be for specified redemption")
                assertEquals(199870L, burn.coinAmount, "Coin burn is for 199,870 coins")
                assertEquals(COMPLETE, CoinRedemptionRecord.findById(redemption.id)!!.status, "Redemption status is now complete")
                assertEquals(CoinBurnStatus.INSERTED, burn.status, "Coin burn is pending")
            }
        }

        @Test
        fun `redemption complete with no bank coin no burn`() {
            val redemption = transaction { insertCoinRedemption(PENDING_REDEEM) }
            insertTxStatus(
                redemption.id.value,
                txHash = randomTxHash(),
                txType = TxType.REDEEM_CONTRACT,
                txStatus = TxStatus.COMPLETE
            )

            val txBuilder = GetTxResponse.newBuilder()
            jsonParser.merge(javaClass.getResource("/proto-examples/redeem-tx-no-coin.json").readText(), txBuilder)

            whenever(pbcServiceMock.getTransaction(any())).thenReturn(txBuilder.build())

            transaction { coinRedemptionService.eventComplete(redemption) }

            verify(pbcServiceMock, times(1)).getTransaction(any())
            verify(bankClientMock, times(1)).depositFiat(any())

            transaction {
                assertEquals(true, CoinBurnRecord.all().empty(), "No burn records")
                assertEquals(COMPLETE, CoinRedemptionRecord.findById(redemption.id)!!.status, "Redemption status is now complete")
            }
        }
    }
}
