package io.provenance.digitalcurrency.consortium.stream

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus
import io.provenance.digitalcurrency.consortium.domain.TST
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.getDefaultTransactionResponse
import io.provenance.digitalcurrency.consortium.getErrorTransactionResponse
import io.provenance.digitalcurrency.consortium.getPendingTransactionResponse
import io.provenance.digitalcurrency.consortium.randomTxHash
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime

class ExpiredEventReaperTest : BaseIntegrationTest() {
    private lateinit var expiredEventReaper: ExpiredEventReaper

    @Autowired
    lateinit var pbcService: PbcService

    @BeforeAll
    fun beforeAll() {
        expiredEventReaper = ExpiredEventReaper(pbcService)
    }

    @BeforeEach
    fun beforeEach() {
        reset(pbcService)
    }

    @Test
    fun `pending status, tx successful, update status to COMPLETE`() {
        val txHash = randomTxHash()
        val receipt = insertCoinRedemption(CoinRedemptionStatus.INSERTED)
        insertTxStatus(
            receipt.id.value,
            txHash,
            TxType.BURN_CONTRACT,
            TxStatus.PENDING,
            OffsetDateTime.now().minusSeconds(60)
        )
        val txResponseSuccess = getDefaultTransactionResponse(txHash)

        whenever(pbcService.getTransaction(any())).thenReturn(txResponseSuccess)

        expiredEventReaper.pollExpiredTransactions()

        transaction {
            val txStatusResults = TxStatusRecord.find { TST.txHash eq txHash }
            assertEquals(txStatusResults.count(), 1)
            assertEquals(TxStatus.COMPLETE, txStatusResults.firstOrNull()?.status)
        }
    }

    @Test
    fun `pending status, tx failed, update status to ERROR`() {
        val txHash = randomTxHash()
        val receipt = insertCoinRedemption(CoinRedemptionStatus.INSERTED)
        insertTxStatus(
            receipt.id.value,
            txHash,
            TxType.BURN_CONTRACT,
            TxStatus.PENDING,
            OffsetDateTime.now().minusSeconds(60)
        )
        val txResponseFail = getErrorTransactionResponse(txHash)

        whenever(pbcService.getTransaction(any())).thenReturn(txResponseFail)

        expiredEventReaper.pollExpiredTransactions()

        transaction {
            val txStatusResults = TxStatusRecord.find { TST.txHash eq txHash }
            assertEquals(txStatusResults.count(), 1)
            assertEquals(TxStatus.ERROR, txStatusResults.firstOrNull()?.status)
        }
    }

    @Test
    fun `pending status, tx pending, leave status as PENDING`() {
        val txHash = randomTxHash()
        val receipt = insertCoinRedemption(CoinRedemptionStatus.INSERTED)
        insertTxStatus(
            receipt.id.value,
            txHash,
            TxType.BURN_CONTRACT,
            TxStatus.PENDING,
            OffsetDateTime.now().minusSeconds(60)
        )
        val txResponsePending = getPendingTransactionResponse(txHash)

        whenever(pbcService.getTransaction(any())).thenReturn(txResponsePending)

        expiredEventReaper.pollExpiredTransactions()

        transaction {
            val txStatusResults = TxStatusRecord.find { TST.txHash eq txHash }
            assertEquals(txStatusResults.count(), 1)
            assertEquals(TxStatus.PENDING, txStatusResults.firstOrNull()?.status)
        }
    }
}
