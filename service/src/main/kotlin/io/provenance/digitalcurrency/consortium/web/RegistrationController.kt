package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.RegisterAddressRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.service.BankService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import javax.validation.Valid

@Validated
@RestController
@RequestMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
@Api(
    value = "Registration Controller",
    description = "Endpoints for the bank middleware for AML/KYC account registration.",
    produces = MediaType.TEXT_PLAIN_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class RegistrationController(private val bankService: BankService) {

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
        log.info("Registering account:$bankAccountUuid at address:$address")
        bankService.registerAddress(bankAccountUuid, address)
        return ResponseEntity.ok(bankAccountUuid)
    }

    @DeleteMapping("$REGISTRATION_V1/{bankAccountUuid}")
    @ApiOperation(
        value = "Remove an address association",
        notes = """
            Send the bank account uuid as a path variable. This will remove the attribute from the address.
        """
    )
    fun removeAddress(@PathVariable bankAccountUuid: UUID): ResponseEntity<UUID> {
        log.info("Removing registration account:$bankAccountUuid")
        bankService.removeAddress(bankAccountUuid)
        return ResponseEntity.ok(bankAccountUuid)
    }
}
