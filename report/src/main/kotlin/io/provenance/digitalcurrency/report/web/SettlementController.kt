package io.provenance.digitalcurrency.report.web

import io.provenance.digitalcurrency.report.config.EventStreamProperties
import io.provenance.digitalcurrency.report.config.logger
import io.provenance.digitalcurrency.report.domain.EventStreamRecord
import io.provenance.digitalcurrency.report.service.SettlementReportService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping
@Api(
    value = "Settlement Controller",
    tags = ["Settlement API"],
    description = "Endpoints for generating settlement reports.",
    produces = MediaType.TEXT_PLAIN_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class SettlementController(
    eventStreamProperties: EventStreamProperties,
    private val balanceReportService: SettlementReportService
) {

    private val log = logger()
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)

    @GetMapping(SETTLEMENT_V1)
    @ApiOperation(value = "Generate a settlement report")
    fun generateSettlementReport(
        @RequestParam("from_block") @Min(1) fromBlockHeight: Long,
        @RequestParam("to_block") @Min(1) toBlockHeight: Long
    ): ResponseEntity<String> {
        require(fromBlockHeight < toBlockHeight) { "To block must be after from block" }
        transaction {
            require((EventStreamRecord.findById(eventStreamId)?.lastBlockHeight ?: 0) >= toBlockHeight) {
                "To block has not been processed yet"
            }
        }

        log.info("Generating settlement report for from block:$fromBlockHeight to block:$toBlockHeight")
        val body = transaction { balanceReportService.createReport(fromBlockHeight, toBlockHeight) }

        val contentDisposition = ContentDisposition.builder("attachment")
            .filename("settlement-$fromBlockHeight-$toBlockHeight.csv")
            .build()
        val headers = HttpHeaders()
        headers.contentDisposition = contentDisposition

        return ResponseEntity.ok()
            .headers(headers)
            .body(body)
    }
}
