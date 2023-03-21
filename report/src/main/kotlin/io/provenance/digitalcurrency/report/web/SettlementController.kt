package io.provenance.digitalcurrency.report.web

import io.provenance.digitalcurrency.report.api.Page
import io.provenance.digitalcurrency.report.api.PaginatedResponse
import io.provenance.digitalcurrency.report.api.Pagination
import io.provenance.digitalcurrency.report.api.SettlementReportResponse
import io.provenance.digitalcurrency.report.config.EventStreamProperties
import io.provenance.digitalcurrency.report.config.logger
import io.provenance.digitalcurrency.report.domain.EventStreamRecord
import io.provenance.digitalcurrency.report.domain.SRT
import io.provenance.digitalcurrency.report.domain.SettlementReportRecord
import io.provenance.digitalcurrency.report.extension.createCsv
import io.provenance.digitalcurrency.report.extension.toPaginatedResponse
import io.provenance.digitalcurrency.report.extension.toSettlementReportResponse
import io.provenance.digitalcurrency.report.service.SettlementReportService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.jetbrains.exposed.sql.SortOrder.DESC
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(SETTLEMENT_V1)
@Api(
    value = "Settlement Controller",
    tags = ["Settlement API"],
    description = "Endpoints for generating settlement reports.",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
)
class SettlementController(
    eventStreamProperties: EventStreamProperties,
    private val settlementReportService: SettlementReportService,
) {

    private val log = logger()
    private val eventStreamId = UUID.fromString(eventStreamProperties.id)

    @GetMapping
    @ApiOperation(value = "Get paginated settlement reports")
    fun getSettlementReports(
        @RequestParam(required = false, value = "page") page: Int?,
        @RequestParam(required = false, value = "size") size: Int?,
    ): PaginatedResponse<List<SettlementReportResponse>> = transaction {
        val pagination = Page(page ?: 1, size ?: 50)
        val query = SettlementReportRecord.all()

        query
            .orderBy(SRT.created to DESC)
            .limit(pagination.size, pagination.offset())
            .map { it.toSettlementReportResponse() }
            .toPaginatedResponse(Pagination(pagination.page, pagination.size, query.count()))
    }

    @GetMapping("/{uuid}")
    @ApiOperation(value = "Get settlement report by ID")
    fun getSettlementReport(@PathVariable("uuid") uuid: UUID): SettlementReportResponse? = transaction {
        SettlementReportRecord.findById(uuid)?.toSettlementReportResponse()
    }

    @GetMapping("/{uuid}/csv")
    @ApiOperation(value = "Download settlement report as CSV by ID")
    fun getSettlementReportCSV(@PathVariable("uuid") uuid: UUID): ResponseEntity<String> = transaction {
        val record = SettlementReportRecord.findById(uuid)
        requireNotNull(record) { "Settlement report not found" }

        val contentDisposition = ContentDisposition.builder("attachment")
            .filename("settlement-${record.fromBlockHeight}-${record.toBlockHeight}.csv")
            .build()
        val headers = HttpHeaders()
        headers.contentDisposition = contentDisposition

        ResponseEntity.ok()
            .headers(headers)
            .body(record.createCsv())
    }

    @PostMapping
    @ApiOperation(value = "Generate a settlement report")
    fun createSettlementReport(
        @RequestParam("from_block")
        @Min(1)
        fromBlockHeight: Long,
        @RequestParam("to_block")
        @Min(1)
        toBlockHeight: Long,
    ): SettlementReportResponse {
        require(fromBlockHeight < toBlockHeight) { "To block must be after from block" }
        transaction {
            require((EventStreamRecord.findById(eventStreamId)?.lastBlockHeight ?: 0) >= toBlockHeight) {
                "To block has not been processed yet"
            }
        }

        log.info("Generating settlement report for from block:$fromBlockHeight to block:$toBlockHeight")
        return transaction { settlementReportService.createReport(fromBlockHeight, toBlockHeight).toSettlementReportResponse() }
    }
}
