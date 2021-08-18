package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinBurnStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import org.springframework.stereotype.Service

@Service
class CoinBurnService(
    private val pbcService: PbcService,
    private val bankClient: BankClient
) {
    private val log = logger()

    fun createEvent(coinBurnRecord: CoinBurnRecord) {
        // There should not be any tx events at this point or all event should have an error status
        val existingEvents: List<TxStatusRecord> =
            TxStatusRecord.findByTxRequestUuid(coinBurnRecord.id.value)
        check(
            existingEvents.isEmpty() ||
                existingEvents.filter { it.status == TxStatus.ERROR }.size == existingEvents.size
        ) { "Burn/swap contract already called" }

        try {
            val txResponse = pbcService.burn(amount = coinBurnRecord.coinAmount.toBigInteger()).txResponse
            log.info("Burn tx hash: ${txResponse.txhash}")
            TxStatusRecord.insert(
                txResponse = txResponse,
                txRequestUuid = coinBurnRecord.id.value,
                type = TxType.BURN_CONTRACT
            )
            CoinBurnRecord.updateStatus(coinBurnRecord.id.value, CoinBurnStatus.PENDING_BURN)
        } catch (e: Exception) {
            log.error("Burn contract failed; it will retry.", e)
        }
    }

    fun eventComplete(coinBurnRecord: CoinBurnRecord) {
        val completedEvent: TxStatusRecord? =
            TxStatusRecord.findByTxRequestUuid(coinBurnRecord.id.value).toList().firstOrNull {
                (it.status == TxStatus.COMPLETE) && (it.type == TxType.BURN_CONTRACT)
            }

        if (completedEvent != null) {
            log.info("Completing burn contract by notifying bank to send fiat")
            if (coinBurnRecord.coinRedemption != null) {
                try {
                    val response = bankClient.depositFiat(
                        DepositFiatRequest(
                            uuid = coinBurnRecord.id.value,
                            bankAccountUUID = coinBurnRecord.coinRedemption!!.addressRegistration.bankAccountUuid,
                            amount = coinBurnRecord.fiatAmount
                        )
                    )
                    log.info("response $response")
                    CoinBurnRecord.updateStatus(coinBurnRecord.id.value, CoinBurnStatus.COMPLETE)
                } catch (e: Exception) {
                    log.error("sending fiat deposit request to bank failed; it will retry.", e)
                }
            } else {
                // no redemption for the burn - just complete it
                CoinBurnRecord.updateStatus(coinBurnRecord.id.value, CoinBurnStatus.COMPLETE)
            }
        } else {
            log.info("Blockchain event not completed for burn contract event yet")
        }
    }
}
