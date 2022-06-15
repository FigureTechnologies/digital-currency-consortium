package io.provenance.digitalcurrency.report.service

import io.provenance.digitalcurrency.report.domain.CoinMovementRecord
import io.provenance.digitalcurrency.report.domain.SettlementNetEntryRecord
import io.provenance.digitalcurrency.report.domain.SettlementReportRecord
import io.provenance.digitalcurrency.report.domain.SettlementWireEntryRecord
import org.springframework.stereotype.Service
import kotlin.math.abs

@Service
class SettlementReportService {

    fun createReport(fromBlockHeight: Long, toBlockHeight: Long): SettlementReportRecord {
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
                        receiverAmount >= abs(senderAmount) -> {
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

        return record
    }
}
