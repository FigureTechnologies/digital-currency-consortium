package io.provenance.digitalcurrency.consortium.web

import cosmos.base.v1beta1.CoinOuterClass.Coin
import io.provenance.digitalcurrency.consortium.api.GrantRequest
import io.provenance.digitalcurrency.consortium.api.MemberResponse
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
import io.provenance.digitalcurrency.consortium.service.PbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
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
class GovernanceController(private val rpcClient: RpcClient, private val pbcService: PbcService) {

    private val log = logger()

    @GetMapping(MEMBER_V1, produces = [MediaType.APPLICATION_JSON_VALUE])
    @ApiOperation(value = "Get member banks of the consortium")
    fun getMembers(): ResponseEntity<List<MemberResponse>> {
        log.info("Retrieving members")

        return pbcService.getMembers()
            .members
            .map {
                MemberResponse(
                    id = it.id,
                    name = it.name,
                    joined = OffsetDateTime.parse(rpcClient.fetchBlock(it.joined).block.header.time),
                    kycAttributes = it.kycAttributes
                )
            }
            .sortedBy { it.joined }
            .let { ResponseEntity.ok(it) }
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
