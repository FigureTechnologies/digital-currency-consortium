package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.annotation.NotTest
import io.provenance.digitalcurrency.consortium.api.BankSettlementRequest
import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.logger
import io.provenance.digitalcurrency.consortium.config.withMdc
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
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
@NotTest
class MarkerTransferQueue(
    private val bankClient: BankClient,
    private val pbcService: PbcService,
    coroutineProperties: CoroutineProperties,
) :
    ActorModel<MarkerTransferDirective, MarkerTransferOutcome> {
    private val log = logger()

    @EventListener(DataSourceConnectedEvent::class)
    fun startProcessing() {
        log.info("start marker transfer queueing framework")
        start()
    }

    override val numWorkers: Int = coroutineProperties.numWorkers
    override val pollingDelayMillis: Long = coroutineProperties.pollingDelayMs

    override suspend fun loadMessages(): List<MarkerTransferDirective> =
        transaction {
            MarkerTransferRecord.findTxnCompleted().map { MarkerTransferDirective(it.id.value) }
        }

    override fun processMessage(message: MarkerTransferDirective): MarkerTransferOutcome {
        transaction {
            MarkerTransferRecord.findTxnCompletedForUpdate(message.id).first().let { transfer ->
                withMdc(*transfer.mdc()) {
                    // TODO - handle if active address no longer exists due to deregistration
                    when (val registration = AddressRegistrationRecord.findActiveByAddress(transfer.fromAddress)) {
                        is AddressRegistrationRecord -> {
                            // Let bank know of dcc deposit to member bank from registered account.
                            transfer.sendAndComplete("fiat deposit") {
                                bankClient.depositFiat(
                                    DepositFiatRequest(
                                        uuid = transfer.id.value,
                                        bankAccountUUID = registration.bankAccountUuid,
                                        amount = transfer.fiatAmount
                                    )
                                )
                            }
                        }
                        null -> {
                            // TODO - cache member banks
                            // Let bank know of dcc deposit from another member bank.
                            pbcService.getMembers().members
                                .firstOrNull { it.id == transfer.fromAddress }
                                ?.let { member ->
                                    transfer.sendAndComplete("fiat settlement") {
                                        bankClient.settleFiat(
                                            BankSettlementRequest(
                                                uuid = transfer.id.value,
                                                bankMemberAddress = member.id,
                                                bankMemberName = member.name,
                                                amount = transfer.fiatAmount
                                            )
                                        )
                                    }
                                }
                                ?: run {
                                    // Marker transfer cannot be handled
                                    MarkerTransferRecord.updateStatus(transfer.id.value, TxStatus.ERROR)
                                    log.error("Address ${transfer.fromAddress} is not registered")
                                }
                        }
                    }
                }
            }
        }
        return MarkerTransferOutcome(message.id)
    }

    private fun MarkerTransferRecord.sendAndComplete(type: String, sendFun: () -> Unit) {
        try {
            sendFun()
            MarkerTransferRecord.updateStatus(id.value, TxStatus.ACTION_COMPLETE)
        } catch (e: Exception) {
            log.error("sending $type request to bank failed; it will retry.", e)
        }
    }

    override fun onMessageSuccess(result: MarkerTransferOutcome) {
        log.info("marker transfer queue successfully processed tx request uuid ${result.id}.")
    }

    override fun onMessageFailure(message: MarkerTransferDirective, e: Exception) {
        log.error("marker transfer queue got error for tx request uuid ${message.id}", e)
    }
}
