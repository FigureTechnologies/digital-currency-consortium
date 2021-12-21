package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.pbclient.api.grpc.BaseReq.Companion.DEFAULT_GAS_DENOM
import io.provenance.digitalcurrency.consortium.service.BalanceReportService
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Reporting Controller",
    tags = ["Reporting API"],
    description = "Endpoints for the bank middleware to call for reporting and data queries.",
    produces = MediaType.TEXT_PLAIN_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class ReportingController(
    private val balanceReportService: BalanceReportService,
    private val pbcService: PbcService
) {

    private val log = logger()

    @PostMapping(BALANCES_V1)
    @ApiOperation(value = "Kickoff a daily balance report")
    fun createBalanceReport(): ResponseEntity<String> {
        log.info("Creating balance report")
        balanceReportService.createReport()

        return ResponseEntity.ok("Report initialized")
    }

    @GetMapping(GAS_BALANCE_V1)
    @ApiOperation(value = "Get the balance of gas for an address")
    fun getGasBalance(): ResponseEntity<String> {
        log.info("Getting gas balance")
        return ResponseEntity.ok(pbcService.getCoinBalance(denom = DEFAULT_GAS_DENOM))
    }
}
