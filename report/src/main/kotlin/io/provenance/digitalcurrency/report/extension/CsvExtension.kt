package io.provenance.digitalcurrency.report.extension

import io.provenance.digitalcurrency.report.domain.SettlementReportRecord
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.StringWriter

private val csvFormat = CSVFormat.Builder.create(CSVFormat.RFC4180)
    .setIgnoreEmptyLines(true)
    .setSkipHeaderRecord(true)
    .build()

fun SettlementReportRecord.createCsv(): String {
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
