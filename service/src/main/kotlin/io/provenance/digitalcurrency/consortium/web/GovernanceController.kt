package io.provenance.digitalcurrency.consortium.web

import cosmos.base.v1beta1.CoinOuterClass.Coin
import io.provenance.digitalcurrency.consortium.api.GrantRequest
import io.provenance.digitalcurrency.consortium.api.JoinConsortiumRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@Validated
@RestController
@RequestMapping(produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Governance Controller",
    tags = ["Governance API"],
    description = "Endpoints for the bank middleware to call for governance actions.",
    produces = MediaType.TEXT_PLAIN_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class GovernanceController(private val pbcService: PbcService) {

    private val log = logger()

    @PostMapping(MEMBER_V1)
    @ApiOperation(value = "Proposal to join the consortium as a member bank")
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
    @ApiOperation(value = "Accept joining the consortium as a member bank")
    fun acceptProposal(): ResponseEntity<String> {
        log.info("Try accepting consortium proposal")
        pbcService.accept()
        return ResponseEntity.ok("Proposal Accepted")
    }

    @PostMapping(GRANTS_V1)
    @ApiOperation(value = "Grant authz allowance so smart contract has permission to move restricted coins out of member bank address")
    fun grantAuth(
        @Valid
        @RequestBody request: GrantRequest
    ): ResponseEntity<String> {
        log.info("Trying to grant authz: $request")
        val (coinRequests, expiration) = request
        val coins = coinRequests
            .map { Coin.newBuilder().setDenom(it.denom).setAmount(it.amount.toString()).build() }
            .sortedBy { it.denom }
        pbcService.grantAuthz(coins, expiration)
        return ResponseEntity.ok("Granted Authz Set")
    }
}
