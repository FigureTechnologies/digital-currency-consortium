package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.BankClientProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinBurnRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.coinsAmount
import io.provenance.digitalcurrency.consortium.extension.findWithdrawEvent
import io.provenance.digitalcurrency.consortium.extension.isSingleTx
import org.springframework.stereotype.Service

@Service
class CoinRedemptionService(
    private val bankClient: BankClient,
    private val pbcService: PbcService,
    bankClientProperties: BankClientProperties,
    private val pbcTimeoutService: PbcTimeoutService
) {
    private val log = logger()
    private val bankDenom = bankClientProperties.denom

    fun createEvent(coinRedemptionRecord: CoinRedemptionRecord) {
        check(coinRedemptionRecord.txHash.isNullOrBlank()) { "Redemption has already been processed" }

        try {
            val timeoutHeight = pbcTimeoutService.getBlockTimeoutHeight()

            val txResponse = pbcService.redeem(coinRedemptionRecord.coinAmount.toBigInteger(), timeoutHeight).txResponse
            log.info("Redeem tx hash: ${txResponse.txhash}")
            coinRedemptionRecord.updateToPending(txResponse.txhash, timeoutHeight)
        } catch (e: Exception) {
            log.error("redeem contract failed; it will retry.", e)
        }
    }

    fun eventComplete(coinRedemptionRecord: CoinRedemptionRecord) {
        log.info("Completing redemption by notifying bank to send fiat")

        val txResponse = pbcService.getTransaction(coinRedemptionRecord.txHash!!)!!.txResponse
        check(txResponse.isSingleTx()) { "Multi tx redemption not supported id:${coinRedemptionRecord.id.value}, tx:${coinRedemptionRecord.txHash}" }

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
            CoinRedemptionRecord.updateStatus(coinRedemptionRecord.id.value, TxStatus.ACTION_COMPLETE)
        } catch (e: Exception) {
            log.error("sending fiat deposit request to bank failed; it will retry.", e)
        }
    }
}
