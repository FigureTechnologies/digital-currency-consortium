package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.extension.coinsAmount
import io.provenance.digitalcurrency.consortium.extension.findWithdrawEvent
import io.provenance.digitalcurrency.consortium.extension.isSingleTx
import org.springframework.stereotype.Service

@Service
class CoinRedemptionService(
    private val bankClient: BankClient,
    private val pbcService: PbcService,
    bankClientProperties: BankClientProperties
) {
    private val log = logger()
    private val bankDenom = bankClientProperties.denom

    fun createEvent(coinRedemptionRecord: CoinRedemptionRecord) {
        // There should not be any tx events at this point or all event should have an error status
        val existingEvents: List<TxStatusRecord> =
            TxStatusRecord.findByTxRequestUuid(coinRedemptionRecord.id.value)
        check(
            existingEvents.isEmpty() ||
                existingEvents.filter { it.status == TxStatus.ERROR }.size == existingEvents.size
        ) { "Redemption event already exists" }

        try {
            val txResponse = pbcService.redeem(coinRedemptionRecord.coinAmount.toBigInteger()).txResponse
            log.info("Redeem tx hash: ${txResponse.txhash}")
            TxStatusRecord.insert(
                txResponse = txResponse,
                txRequestUuid = coinRedemptionRecord.id.value,
                type = TxType.REDEEM_CONTRACT
            )
            CoinRedemptionRecord.updateStatus(coinRedemptionRecord.id.value, CoinRedemptionStatus.PENDING_REDEEM)
        } catch (e: Exception) {
            log.error("redeem contract failed; it will retry.", e)
        }
    }

    fun eventComplete(coinRedemptionRecord: CoinRedemptionRecord) {
        val completedEvent: TxStatusRecord? =
            TxStatusRecord.findByTxRequestUuid(coinRedemptionRecord.id.value).firstOrNull {
                (it.status == TxStatus.COMPLETE) && (it.type == TxType.REDEEM_CONTRACT)
            }

        if (completedEvent != null) {
            log.info("Completing redemption by notifying bank to send fiat")

            val txResponse = pbcService.getTransaction(completedEvent.txHash)!!.txResponse
            // TODO - once we batch, we'll need to work backwards to map log with redemption
            check(txResponse.isSingleTx()) { "Multi tx redemption not supported id:${completedEvent.id.value}, tx:${completedEvent.txHash}" }

            try {
                bankClient.depositFiat(
                    DepositFiatRequest(
                        uuid = coinRedemptionRecord.id.value,
                        bankAccountUUID = coinRedemptionRecord.addressRegistration.bankAccountUuid,
                        amount = coinRedemptionRecord.fiatAmount
                    )
                )

                // If we redeemed bank-specific coin, initialize burn for bank-specific portion
                txResponse.logsList.first().findWithdrawEvent(bankDenom)?.run {
                    CoinBurnRecord.insert(coinRedemption = coinRedemptionRecord, coinAmount = coinsAmount(bankDenom)).also {
                        log.info("Setting up burn of ${it.coinAmount}")
                    }
                }
                CoinRedemptionRecord.updateStatus(coinRedemptionRecord.id.value, CoinRedemptionStatus.COMPLETE)
            } catch (e: Exception) {
                log.error("sending fiat deposit request to bank failed; it will retry.", e)
            }
        } else {
            log.info("Blockchain event not completed for redemption contract event yet")
        }
    }
}
