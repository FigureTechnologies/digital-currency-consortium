package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

// TODO this is an interface to the bank's middleware
@Api(
    value = "3rd Party Bank Interface",
    description = "Endpoints for the digital currency middleware to call at the bank to initiate fiat deposits.",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
@RestController("BankController")
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class BankController {
    @PostMapping(FIAT_DEPOSIT)
    @ApiOperation(
        value = "Request that the bank deposit fiat for coin redemption",
        notes = "Notify the bank that a customer has request to redeem coin for fiat to their bank account."
    )
    fun depositFiat(
        @Valid
        @ApiParam(value = "DepositFiatRequest")
        @RequestBody request: DepositFiatRequest
    ) {
        logger().info("Depositing fiat $request")
    }
}
