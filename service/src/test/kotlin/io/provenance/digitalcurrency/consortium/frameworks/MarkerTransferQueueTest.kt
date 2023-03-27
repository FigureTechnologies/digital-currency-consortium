package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.DEFAULT_AMOUNT
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus.ERROR
import io.provenance.digitalcurrency.consortium.domain.TxStatus.TXN_COMPLETE
import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import io.provenance.digitalcurrency.consortium.randomTxHash
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class MarkerTransferQueueTest : BaseIntegrationTest() {
    @Autowired
    lateinit var bankClientMock: BankClient

    @Autowired
    private lateinit var coroutineProperties: CoroutineProperties

    @Autowired
    private lateinit var serviceProperties: ServiceProperties

    private lateinit var markerTransferQueue: MarkerTransferQueue

    @BeforeEach
    fun beforeAll() {
        reset(bankClientMock)

        markerTransferQueue = MarkerTransferQueue(
            bankClient = bankClientMock,
            coroutineProperties = coroutineProperties,
        )
    }

    @Test
    fun `marker transfer for registered account should notify bank of fiat deposit`() {
        val bankAccountUuid = UUID.randomUUID()
        transaction { insertRegisteredAddress(bankAccountUuid, TEST_ADDRESS, TXN_COMPLETE) }
        val uuid = insertMarkerTransfer(
            txHash = randomTxHash(),
            fromAddress = TEST_ADDRESS,
            toAddress = TEST_MEMBER_ADDRESS,
            status = TXN_COMPLETE,
            denom = serviceProperties.dccDenom,
        ).id.value

        markerTransferQueue.processMessage(MarkerTransferDirective(uuid))

        verify(bankClientMock).depositFiat(
            DepositFiatRequest(
                uuid = uuid,
                bankAccountUUID = bankAccountUuid,
                amount = DEFAULT_AMOUNT.toUSDAmount(),
            ),
        )
    }

    @Test
    fun `marker transfer for unknown from address will error`() {
        val uuid = insertMarkerTransfer(
            txHash = randomTxHash(),
            fromAddress = "somebadfromaddress",
            toAddress = TEST_MEMBER_ADDRESS,
            status = TXN_COMPLETE,
            denom = serviceProperties.dccDenom,
        ).id.value

        markerTransferQueue.processMessage(MarkerTransferDirective(uuid))

        verify(bankClientMock, never()).depositFiat(any())

        Assertions.assertEquals(ERROR, transaction { MarkerTransferRecord.findById(uuid)!!.status }, "Marker transfer marked as error")
    }
}
