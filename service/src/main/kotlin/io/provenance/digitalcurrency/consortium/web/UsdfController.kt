package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RedeemBurnCoinRequest
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.service.BankService
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
@RestController("UsdfController")
@RequestMapping(produces = [MediaType.TEXT_PLAIN_VALUE])
@Api(
    value = "Usdf Controller",
    description = "Endpoints for the bank middleware to call to execution USDF-based actions.",
    produces = MediaType.TEXT_PLAIN_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class UsdfController(private val bankService: BankService) {

    private val log = logger()

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
        log.info("Minting $uuid amount:$amount to bank:$bankAccountUuid")
        bankService.mintCoin(uuid, bankAccountUuid, amount)
        return ResponseEntity.ok(uuid)
    }

    @PostMapping(REDEEM_BURN_V1)
    @ApiOperation(
        value = "Redeem and burn dcc/reserve token",
        notes = """
            Request that the middleware redeem and burn dcc and reserve token.
            """
    )
    fun redeemBurn(
        @Valid
        @ApiParam(value = "MintCoinRequest")
        @RequestBody request: RedeemBurnCoinRequest
    ): ResponseEntity<UUID> {
        val (uuid, amount) = request
        log.info("Redeem and burning $uuid amount:$amount")
        bankService.redeemBurnCoin(uuid, amount)
        return ResponseEntity.ok(uuid)
    }
}
