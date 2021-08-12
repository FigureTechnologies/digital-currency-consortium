package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.ConsortiumAcceptRequest
import io.provenance.digitalcurrency.consortium.api.ConsortiumJoinRequest
import io.provenance.digitalcurrency.consortium.api.ConsortiumVoteRequest
import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RegisterAddressRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
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
@Api(
    value = "Digital Currency Controller",
    description = "Endpoints for the bank middleware to call to initiate smart contract executions.",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class DigitalCurrencyController {
    private val log = logger()

    @PostMapping(CONSORTIUM_JOIN_V1)
    @ApiOperation(
        value = "Join the consortium",
        notes = "Request to join the consortium of banks. This should only be done once at the beginning of the relationship with the consortium."
    )
    fun joinConsortium(
        @Valid
        @ApiParam(value = "ConsortiumJoinRequest")
        @RequestBody request: ConsortiumJoinRequest
    ) {
        log.info("Joining consortium $request")
    }

    @PostMapping(CONSORTIUM_VOTE_V1)
    @ApiOperation(
        value = "Vote on a proposal in the consortium",
        notes = "Submit a yes or no vote for a proposal to the consortium. "
    )
    fun voteOnConsortiumProposal(
        @Valid
        @ApiParam(value = "ConsortiumVoteRequest")
        @RequestBody request: ConsortiumVoteRequest
    ) {
        log.info("Voting on consortium proposal $request")
    }

    @PostMapping(CONSORTIUM_ACCEPT_V1)
    @ApiOperation(
        value = "Accept a proposal in the consortium",
        notes = "Accept a proposal to the consortium"
    )
    fun acceptConsortiumProposal(
        @Valid
        @ApiParam(value = "ConsortiumAcceptRequest")
        @RequestBody request: ConsortiumAcceptRequest
    ) {
        log.info("Accepting consortium proposal $request")
    }

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
    ) {
        log.info("Registering address $request")
    }

    @PostMapping(MINT_V1)
    @ApiOperation(
        value = "Mint coin to a registered address",
        notes = "Request that the middleware mint coin corresponding to a fiat deposit from a customer."
    )
    fun mintCoin(
        @Valid
        @ApiParam(value = "MintCoinRequest")
        @RequestBody request: MintCoinRequest
    ) {
        log.info("Minting $request")
    }
}
