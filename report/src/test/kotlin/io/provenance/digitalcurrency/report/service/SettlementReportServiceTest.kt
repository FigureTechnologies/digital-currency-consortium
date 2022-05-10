package io.provenance.digitalcurrency.report.service

import io.provenance.digitalcurrency.report.DatabaseTest
import io.provenance.digitalcurrency.report.TestContainer
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.math.abs

@TestContainer
class SettlementReportServiceTest : DatabaseTest() {

    @Autowired
    lateinit var settlementReportService: SettlementReportService

    @Test
    fun `test create settlement report`() {
        transaction {
            insertCoinMovement(fromMemberId = "bank1", toMemberId = "bank1", blockHeight = 1, amount = 100 * 100)
            insertCoinMovement(fromMemberId = "bank1", toMemberId = "bank2", blockHeight = 2, amount = 200 * 100)
            insertCoinMovement(fromMemberId = "bank2", toMemberId = "bank1", blockHeight = 3, amount = 300 * 100)
            insertCoinMovement(fromMemberId = "bank3", toMemberId = "bank4", blockHeight = 4, amount = 400 * 100)
            insertCoinMovement(fromMemberId = "bank4", toMemberId = "bank5", blockHeight = 5, amount = 500 * 100)
            insertCoinMovement(fromMemberId = "bank2", toMemberId = "bank3", blockHeight = 6, amount = 600 * 100)
            insertCoinMovement(fromMemberId = "bank5", toMemberId = "bank1", blockHeight = 7, amount = 700 * 100)
            insertCoinMovement(fromMemberId = "bank5", toMemberId = "bank2", blockHeight = 8, amount = 800 * 100)
            insertCoinMovement(fromMemberId = "bank3", toMemberId = "bank4", blockHeight = 9, amount = 900 * 100)
            insertCoinMovement(fromMemberId = "bank2", toMemberId = "bank1", blockHeight = 10, amount = 1000 * 100)
            insertCoinMovement(fromMemberId = "bank2", toMemberId = "bank3", blockHeight = 11, amount = 1100 * 100)
            insertCoinMovement(fromMemberId = "bank2", toMemberId = "bank3", blockHeight = 12, amount = 1200 * 100)
            insertCoinMovement(fromMemberId = "bank4", toMemberId = "bank5", blockHeight = 13, amount = 1300 * 100)
            insertCoinMovement(fromMemberId = "bank1", toMemberId = "bank2", blockHeight = 14, amount = 1400 * 100)
        }

        transaction {
            val record = settlementReportService.createReport(fromBlockHeight = 1, toBlockHeight = 14)
            val netEntries = record.netEntries
            val wireEntries = record.wireEntries

            Assertions.assertEquals(5, netEntries.count())
            Assertions.assertEquals(40000L, netEntries.first { it.memberId == "bank1" }.amount)
            Assertions.assertEquals(-180000L, netEntries.first { it.memberId == "bank2" }.amount)
            Assertions.assertEquals(160000L, netEntries.first { it.memberId == "bank3" }.amount)
            Assertions.assertEquals(-50000L, netEntries.first { it.memberId == "bank4" }.amount)
            Assertions.assertEquals(30000L, netEntries.first { it.memberId == "bank5" }.amount)

            Assertions.assertEquals(4, wireEntries.count())
            Assertions.assertEquals(160000L, wireEntries.first { it.fromMemberId == "bank2" && it.toMemberId == "bank3" }.amount)
            Assertions.assertEquals(20000L, wireEntries.first { it.fromMemberId == "bank2" && it.toMemberId == "bank1" }.amount)
            Assertions.assertEquals(30000L, wireEntries.first { it.fromMemberId == "bank4" && it.toMemberId == "bank5" }.amount)
            Assertions.assertEquals(20000L, wireEntries.first { it.fromMemberId == "bank4" && it.toMemberId == "bank1" }.amount)

            // Sanity check
            Assertions.assertEquals(netEntries.sumOf { abs(it.amount) } / 2, wireEntries.sumOf { it.amount })
        }
    }
}
