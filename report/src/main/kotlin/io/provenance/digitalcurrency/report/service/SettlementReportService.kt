package io.provenance.digitalcurrency.report.service

import io.provenance.digitalcurrency.report.domain.CoinMovementRecord
import io.provenance.digitalcurrency.report.domain.SettlementNetEntryRecord
import io.provenance.digitalcurrency.report.domain.SettlementReportRecord
import io.provenance.digitalcurrency.report.domain.SettlementWireEntryRecord
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Service
import java.io.StringWriter

@Service
class SettlementReportService {

    private val csvFormat = CSVFormat.Builder.create(CSVFormat.RFC4180)
        .setIgnoreEmptyLines(true)
        .setSkipHeaderRecord(true)
        .build()

    fun createReport(fromBlockHeight: Long, toBlockHeight: Long): String {
        val record = SettlementReportRecord.insert(fromBlockHeight, toBlockHeight)
        val netPositions = CoinMovementRecord.findNetPositions(fromBlockHeight, toBlockHeight)
        check(netPositions.sumOf { (_, amount) -> amount } == 0L) { "Unexpected net positions do not net to zero" }

        // Store net movements
        netPositions.map { (memberId, amount) -> SettlementNetEntryRecord.insert(record, memberId, amount) }

        // Calculate and generate wire movements
        val (senders, recs) = netPositions.partition { (_, amount) -> amount < 0 }
        val receivers = recs.filter { (_, amount) -> amount > 0L }.toMutableList()
        senders // TODO - optimize algorithm for optimized minimal number of wire transfers
            .sortedBy { (_, amount) -> amount }
            .forEach { (senderId, amount) ->
                var senderAmount = amount

                while (senderAmount < 0L) {
                    receivers.sortByDescending { (_, amount) -> amount } // sort in place for largest receiver remaining
                    var receiver = receivers.first() // use largest receiver
                    val (receiverId, receiverAmount) = receiver

                    when {
                        receiverAmount >= senderAmount -> {
                            SettlementWireEntryRecord.insert(record, senderId, receiverId, -senderAmount)
                            receiver = receiver.copy(second = receiverAmount + senderAmount)
                            senderAmount = 0L
                        }
                        else -> {
                            SettlementWireEntryRecord.insert(record, senderId, receiverId, receiverAmount)
                            receiver = receiver.copy(second = 0)
                            senderAmount += receiverAmount
                        }
                    }

                    // Update or remove first receiver
                    if (receiver.second > 0L) {
                        receivers[0] = receiver
                    } else {
                        receivers.removeAt(0)
                    }
                }
            }

        return record.createCsv()
    }

    private fun SettlementReportRecord.createCsv(): String {
        val stringWriter = StringWriter()
        val csvPrinter = CSVPrinter(stringWriter, csvFormat)
        csvPrinter.use {
            // Print net balances
            csvPrinter.printRecord(listOf("Member", "Net Position"))
            netEntries.forEach { csvPrinter.printRecord(listOf(it.memberId, it.amount.toString())) }
            csvPrinter.println()

            // Print wire transfers
            csvPrinter.printRecord(listOf("Sender", "Receiver", "Amount"))
            wireEntries.forEach { csvPrinter.printRecord(listOf(it.fromMemberId, it.toMemberId, it.amount.toString())) }
        }

        return stringWriter.toString()
    }
}
