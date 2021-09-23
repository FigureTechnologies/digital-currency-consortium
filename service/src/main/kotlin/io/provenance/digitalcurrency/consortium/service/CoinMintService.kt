package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import org.springframework.stereotype.Service

@Service
class CoinMintService(
    private val pbcService: PbcService,
    private val bankClient: BankClient
) {
    private val log = logger()

    fun createEvent(coinMintRecord: CoinMintRecord) {
        // There should not be any tx events at this point or all event should have an error status
        val existingEvents: List<TxStatusRecord> =
            TxStatusRecord.findByTxRequestUuid(coinMintRecord.id.value)
        check(
            existingEvents.isEmpty() ||
                existingEvents.filter { it.status == TxStatus.ERROR }.size == existingEvents.size
        ) { "Mint/swap contract already called" }

        try {
            val txResponse = pbcService.mintAndSwap(
                amount = coinMintRecord.coinAmount.toBigInteger(),
                address = coinMintRecord.addressRegistration.address
            ).txResponse
            log.info("Mint/swap tx hash: ${txResponse.txhash}")
            TxStatusRecord.insert(
                txResponse = txResponse,
                txRequestUuid = coinMintRecord.id.value,
                type = TxType.MINT_CONTRACT
            )
            CoinMintRecord.updateStatus(coinMintRecord.id.value, CoinMintStatus.PENDING_MINT)
        } catch (e: Exception) {
            log.error("Mint/swap contract failed; it will retry.", e)
        }
    }

    fun eventComplete(coinMintRecord: CoinMintRecord) {
        val completedEvent: TxStatusRecord? =
            TxStatusRecord.findByTxRequestUuid(coinMintRecord.id.value).toList().firstOrNull {
                (it.status == TxStatus.COMPLETE) && (it.type == TxType.MINT_CONTRACT)
            }

        if (completedEvent != null) {
            log.info("Completing mint/swap contract by notifying bank")
            try {
                bankClient.completeMint(coinMintRecord.id.value)
                CoinMintRecord.updateStatus(coinMintRecord.id.value, CoinMintStatus.COMPLETE)
            } catch (e: Exception) {
                log.error("updating mint status at bank failed; it will retry.", e)
            }
        } else {
            log.info("Blockchain event not completed for mint/swap contract event yet")
        }
    }
}
