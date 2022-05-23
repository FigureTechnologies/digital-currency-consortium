package io.provenance.digitalcurrency.report

import io.provenance.digitalcurrency.report.domain.CMT
import io.provenance.digitalcurrency.report.domain.CoinMovementRecord
import io.provenance.digitalcurrency.report.domain.EST
import io.provenance.digitalcurrency.report.domain.SNET
import io.provenance.digitalcurrency.report.domain.SRT
import io.provenance.digitalcurrency.report.domain.SWET
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import java.time.OffsetDateTime

abstract class DatabaseTest {
    @AfterEach
    fun afterEach() {
        transaction {
            SNET.deleteAll()
            SWET.deleteAll()
            SRT.deleteAll()
            CMT.deleteAll()
            EST.deleteAll()
        }
    }

    fun insertCoinMovement(
        fromAddress: String = "from-address",
        fromMemberId: String = "bank1",
        toAddress: String = "to-address",
        toMemberId: String = "bank2",
        blockHeight: Long = 1,
        amount: Long
    ) = CoinMovementRecord.insert(
        txHash = randomTxHash(),
        fromAddress = fromAddress,
        fromMemberId = fromMemberId,
        toAddress = toAddress,
        toMemberId = toMemberId,
        blockHeight = blockHeight,
        blockTime = OffsetDateTime.now(),
        amount = amount
    )
}
