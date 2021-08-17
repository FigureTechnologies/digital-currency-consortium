package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RegisterAddressRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.service.DigitalCurrencyService
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
import org.springframework.web.context.request.async.DeferredResult
import java.util.concurrent.ForkJoinPool
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
class DigitalCurrencyController(private val digitalCurrencyService: DigitalCurrencyService) {

    private val log by lazy { logger() }

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
    ): DeferredResult<ResponseEntity<*>> {
        val (bankAccountUuid, address) = request
        digitalCurrencyService.registerAddress(bankAccountUuid, address)

        val output = DeferredResult<ResponseEntity<*>>()
        output.setResult(ResponseEntity.ok(bankAccountUuid))

        ForkJoinPool.commonPool().submit {
            log.info("Processing kyc tag in separate thread")
            try {
                digitalCurrencyService.tryKycTag(bankAccountUuid, address)
            } catch (e: InterruptedException) {
                log.error("interrupted deferred thread - could not complete registration for $bankAccountUuid and $address")
            }
        }

        return output
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
    ): ResponseEntity<*> {
        val (uuid, bankAccountUuid, amount) = request
        digitalCurrencyService.mintCoin(uuid, bankAccountUuid, amount)
        return ResponseEntity.ok(uuid)
    }
}
