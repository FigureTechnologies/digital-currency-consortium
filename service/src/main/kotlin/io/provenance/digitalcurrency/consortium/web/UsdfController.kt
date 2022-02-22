package io.provenance.digitalcurrency.consortium.web

import io.provenance.digitalcurrency.consortium.api.MintCoinRequest
import io.provenance.digitalcurrency.consortium.api.RedeemBurnCoinRequest
import io.provenance.digitalcurrency.consortium.api.TransferRequest
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
@RestController
@RequestMapping(produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "USDF Controller",
    tags = ["USDF API"],
    description = "Endpoints for the bank middleware to call to execute USDF-based actions.",
    produces = MediaType.TEXT_PLAIN_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)
class UsdfController(private val bankService: BankService) {

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
        @ApiParam(value = "RedeemBurnRequest")
        @RequestBody request: RedeemBurnCoinRequest
    ): ResponseEntity<UUID> {
        val (uuid, amount) = request
        bankService.redeemBurnCoin(uuid, amount)
        return ResponseEntity.ok(uuid)
    }

    @PostMapping(TRANSFER_V1)
    fun transfer(
        @Valid
        @ApiParam(value = "TransferRequest")
        @RequestBody request: TransferRequest
    ): ResponseEntity<UUID> {
        val (uuid, bankAccountUuid, blockchainAddress, amount) = request
        bankService.transferCoin(uuid, bankAccountUuid, blockchainAddress, amount)
        return ResponseEntity.ok(uuid)
    }
}
