package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.ConsortiumAcceptRequest
import io.provenance.digitalcurrency.consortium.api.ConsortiumJoinRequest
import io.provenance.digitalcurrency.consortium.api.ConsortiumVoteRequest
import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RegisterAccountRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@Validated
@RestController("DigitalCurrencyController")
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Digital Currency Controller", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
class DigitalCurrencyController {
    private val log = logger()

    @PostMapping(CONSORTIUM_JOIN_V1)
    @ApiOperation("Join the consortium")
    fun joinConsortium(@Valid @RequestBody request: ConsortiumJoinRequest) {
        log.info("Joining consortium $request")
    }

    @PostMapping(CONSORTIUM_VOTE_V1)
    @ApiOperation("Join the consortium")
    fun voteOnConsortiumProposal(@Valid @RequestBody request: ConsortiumVoteRequest) {
        log.info("Voting on consortium proposal $request")
    }

    @PostMapping(CONSORTIUM_ACCEPT_V1)
    @ApiOperation("Join the consortium")
    fun acceptConsortiumProposal(@Valid @RequestBody request: ConsortiumAcceptRequest) {
        log.info("Accepting consortium proposal $request")
    }

    @PostMapping(REGISTRATION_V1)
    @ApiOperation("Registers an address associated with an existing bank account")
    fun registerAddress(@Valid @RequestBody request: RegisterAccountRequest) {
        log.info("Registering address $request")
    }

    @PostMapping(MINT_V1)
    @ApiOperation("Mint coin to a registered address")
    fun mintCoin(@Valid @RequestBody request: MintCoinRequest) {
        log.info("Minting $request")
    }

}