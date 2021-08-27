package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.api.BalanceRequest
import io.provenance.digitalcurrency.consortium.api.BalanceRequestItem
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.BalanceReportProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryRecord
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryTable
import io.provenance.digitalcurrency.consortium.domain.BalanceReportRecord
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.concurrent.thread

@Component
class BalanceReportMonitor(
    balanceReportProperties: BalanceReportProperties,
    private val bankClient: BankClient,
) {

    private val log = logger()

    private val pollingDelayMillis: Long = balanceReportProperties.pollingDelayMs.toLong()
    private val pageSize = balanceReportProperties.pageSize.toInt()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start balance report monitor framework")

        thread(start = true, isDaemon = true, name = "BRM-1") {
            while (true) {
                try {
                    iteration()
                    Thread.sleep(pollingDelayMillis)
                } catch (t: Throwable) {
                    log.warn("Could not send batch", t)

                    Thread.sleep(pollingDelayMillis)
                }
            }
        }
    }

    fun iteration() {
        transaction {
            val balanceReport = BalanceReportRecord.findNotSent().forUpdate().firstOrNull()
            if (balanceReport == null || balanceReport.sent != null)
                return@transaction

            log.info("starting balance report push for [${balanceReport.id.value}]")

            val count = BalanceEntryRecord.findByReport(balanceReport).count()
            val totalPages = ((count / pageSize) + if (count % pageSize == 0L) 0 else 1).toInt()
            var currPage = 1

            log.info("balance report has count [$count] total pages [$totalPages}")

            when {
                totalPages > 0 -> {
                    while (currPage <= totalPages) {
                        val offset = (currPage.toLong() - 1L) * pageSize
                        log.debug("querying for page [$currPage] at offset [$offset]")

                        val output = BalanceEntryRecord.findByReport(balanceReport)
                            .limit(pageSize, offset)
                            .orderBy(BalanceEntryTable.address to ASC)
                            .toList()
                            .toOutput(balanceReport, currPage, totalPages)

                        bankClient.persistBalanceReport(output)

                        currPage += 1
                    }
                }
                else -> {
                    log.info("empty report")
                    bankClient.persistBalanceReport(emptyList<BalanceEntryRecord>().toOutput(balanceReport, 0, 0))
                }
            }

            balanceReport.markSent()

            log.info("ending balance report push for [${balanceReport.id.value}]")
        }
    }
}

fun List<BalanceEntryRecord>.toOutput(balanceReport: BalanceReportRecord, page: Int, totalPages: Int) = BalanceRequest(
    requestUuid = balanceReport.id.value,
    recordCount = this.size,
    page = page,
    totalPages = totalPages,
    transactions = this.map { balanceEntry ->
        BalanceRequestItem(
            uuid = balanceEntry.id.value,
            address = balanceEntry.address,
            amount = balanceEntry.amount,
            denom = balanceEntry.denom,
            timestamp = balanceEntry.created
        )
    }
)
