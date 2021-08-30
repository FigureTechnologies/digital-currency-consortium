package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.JoinConsortiumRequest
import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RegisterAddressRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import io.provenance.digitalcurrency.consortium.service.BalanceReportService
import io.provenance.digitalcurrency.consortium.service.DigitalCurrencyService
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@Validated
@RestController("DigitalCurrencyController")
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Digital Currency Controller",
    description = "Endpoints for the bank middleware to call to initiate smart contract executions.",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class DigitalCurrencyController(
    private val balanceReportService: BalanceReportService,
    private val digitalCurrencyService: DigitalCurrencyService,
    private val pbcService: PbcService
) {

    private val log = logger()

    @PostMapping(REGISTRATION_V1)
    @ApiOperation(
        value = "Register an address associated with an existing bank account",
        notes = """
            Send the middleware a blockchain address and the unique id associated with it. The unique id will be 
            used during coin mint (fiat deposits from the customer) and coin redemption (fiat deposits to the customer)
            requests.
        """
    )
    fun registerAddress(
        @Valid
        @ApiParam(value = "RegisterAddressRequest")
        @RequestBody request: RegisterAddressRequest
    ): ResponseEntity<UUID> {
        val (bankAccountUuid, address) = request
        digitalCurrencyService.registerAddress(bankAccountUuid, address)
        return ResponseEntity.ok(bankAccountUuid)
    }

    @PostMapping(MINT_V1)
    @ApiOperation(
        value = "Mint coin to a registered address",
        notes = """
            Request that the middleware mint coin corresponding to a fiat deposit from a customer.
            """
    )
    fun mintCoin(
        @Valid
        @ApiParam(value = "MintCoinRequest")
        @RequestBody request: MintCoinRequest
    ): ResponseEntity<UUID> {
        val (uuid, bankAccountUuid, amount) = request
        digitalCurrencyService.mintCoin(uuid, bankAccountUuid, amount)
        return ResponseEntity.ok(uuid)
    }

    @PostMapping(MEMBER_V1)
    fun joinConsortium(
        @Valid
        @RequestBody request: JoinConsortiumRequest
    ): ResponseEntity<String> {
        log.info("Try joining consortium: $request")
        val (name, maxSupplyUsd) = request
        pbcService.join(name, maxSupplyUsd.toCoinAmount())
        return ResponseEntity.ok("Join Proposal Created")
    }

    // TODO in the future this will accept the proposal id.
    // TODO should validate the proposal exists but this is a hand jam to get this first bank through
    // for now on start up we are accepting ourselves so the id is the bank address
    @PostMapping(ACCEPTS_V1)
    fun acceptProposal(): ResponseEntity<String> {
        log.info("Try accepting consortium proposal")
        pbcService.accept()
        return ResponseEntity.ok("Proposal Accepted")
    }

    @PostMapping(BALANCES_V1)
    @ApiOperation(value = "Kickoff a daily balance report")
    fun createBalanceReport(): ResponseEntity<String> {
        balanceReportService.createReport()

        return ResponseEntity.ok("Report initialized")
    }
}
