package io.provenance.digitalcurrency.consortium.service

import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMintStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.messages.ExecuteMintRequest
import io.provenance.digitalcurrency.consortium.messages.MintRequest
import org.springframework.stereotype.Service

@Service
class CoinMintService(
    private val pbcService: PbcService,
    private val bankClient: BankClient
) {
    private val log = logger()

    fun createEvent(coinMintRecords: List<CoinMintRecord>) {
        coinMintRecords.mapNotNull { coinMintRecord ->
            // There should not be any tx events at this point or all event should have an error status
            val existingEvents: List<TxStatusRecord> =
                TxStatusRecord.findByTxRequestUuid(coinMintRecord.id.value)
            if (!(existingEvents.isEmpty() || existingEvents.filter { it.status == TxStatus.ERROR }.size == existingEvents.size)) {
                log.error("Mint contract already called")
                CoinMintRecord.updateStatus(coinMintRecord.id.value, CoinMintStatus.EXCEPTION)
                null
            } else {
                ExecuteMintRequest(
                    mint = MintRequest(
                        amount = coinMintRecord.coinAmount.toString(),
                        address = coinMintRecord.addressRegistration.address
                    )
                )
            }
        }.takeIf { mintMessages: List<ExecuteMintRequest> ->
            mintMessages.isNotEmpty()
        }?.let { mintMessages ->
            try {
                val txResponse = pbcService.mintBatch(mintMessages).txResponse
                log.info("Mint batch ${coinMintRecords.map { it.id }} tx hash: ${txResponse.txhash}")
                coinMintRecords.forEach { coinMintRecord ->
                    TxStatusRecord.insert(
                        txResponse = txResponse,
                        txRequestUuid = coinMintRecord.id.value,
                        type = TxType.MINT_CONTRACT
                    )
                    CoinMintRecord.updateStatus(coinMintRecord.id.value, CoinMintStatus.PENDING_MINT)
                }
            } catch (e: Exception) {
                log.error("Mint contract failed for ${coinMintRecords.map { it.id }}; it will retry.", e)
            }
        }
    }

    fun eventComplete(coinMintRecord: CoinMintRecord) {
        val completedEvent: TxStatusRecord? =
            TxStatusRecord.findByTxRequestUuid(coinMintRecord.id.value).toList().firstOrNull {
                (it.status == TxStatus.COMPLETE) && (it.type == TxType.MINT_CONTRACT)
            }

        if (completedEvent != null) {
            log.info("Completing mint contract by notifying bank")
            try {
                bankClient.completeMint(coinMintRecord.id.value)
                CoinMintRecord.updateStatus(coinMintRecord.id.value, CoinMintStatus.COMPLETE)
            } catch (e: Exception) {
                log.error("updating mint status at bank failed; it will retry.", e)
            }
        } else {
            log.info("Blockchain event not completed for mint contract event yet")
        }
    }
}
