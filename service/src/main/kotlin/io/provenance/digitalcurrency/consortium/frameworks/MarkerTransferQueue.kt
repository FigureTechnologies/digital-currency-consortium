package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.extension.isFailed
import io.provenance.digitalcurrency.consortium.extension.mdc
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

class MarkerTransferDirective(
    override val id: UUID
) : Directive()

class MarkerTransferOutcome(
    override val id: UUID
) : Outcome()

@Component
class MarkerTransferQueue(
    coroutineProperties: CoroutineProperties,
    private val pbcService: PbcService
) :
    ActorModel<MarkerTransferDirective, MarkerTransferOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start marker transfer queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers.toInt()
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs.toLong()

    override suspend fun loadMessages(): List<MarkerTransferDirective> =
        transaction {
            MarkerTransferRecord.findPending().map { MarkerTransferDirective(it.id.value) }
        }

    override fun processMessage(message: MarkerTransferDirective): MarkerTransferOutcome {
        transaction {
            MarkerTransferRecord.findForUpdate(message.id).first().let { transfer ->
                withMdc(*transfer.mdc()) {
                    pbcService.getTransaction(transfer.txHash)?.txResponse?.takeIf {
                        !it.isFailed()
                    }?.let { txResponse ->
                        val registration = AddressRegistrationRecord.findByAddress(transfer.fromAddress)
                        check(registration != null) { "Address is not registered" }

                        TxStatusRecord.insert(
                            txResponse = txResponse,
                            txRequestUuid = message.id,
                            type = TxType.MARKER_TRANSFER
                        ).also {
                            it.status = TxStatus.COMPLETE
                        }

                        // set up the redeem
                        CoinRedemptionRecord.insert(
                            addressRegistration = registration,
                            coinAmount = transfer.coinAmount
                        )

                        MarkerTransferRecord.updateStatus(transfer.id, MarkerTransferStatus.COMPLETE)
                    }
                }
            }
        }
        return MarkerTransferOutcome(message.id)
    }

    override fun onMessageSuccess(result: MarkerTransferOutcome) {
        log.info("marker transfer queue successfully processed tx request uuid ${result.id}.")
    }

    override fun onMessageFailure(message: MarkerTransferDirective, e: Exception) {
        log.error("marker transfer queue got error for tx request uuid ${message.id}; will retry on next round", e)
    }
}
