package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@Api("3rd Party Bank Interface")
@RestController("BankController")
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class BankController {
    private val log = logger()

    @PostMapping(FIAT_DEPOSIT)
    @ApiOperation("Request that the bank deposit fiat for coin redemption")
    fun depositFiat(@Valid @RequestBody request: DepositFiatRequest) {
        log.info("Depositing fiat $request")
    }
}