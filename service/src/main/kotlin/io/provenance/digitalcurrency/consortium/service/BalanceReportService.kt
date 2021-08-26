package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.BalanceReportRecord
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class BalanceReportService(
    private val pbcService: PbcService,
) {
    private val log = logger()

    fun createReport() {
        log.info("Initializing balance report")

        transaction {
            // TODO - do we need or care about this?
            require(BalanceReportRecord.findPending().empty()) { "Existing pending balance report exists" }

            BalanceReportRecord.insert()
        }
    }
}
