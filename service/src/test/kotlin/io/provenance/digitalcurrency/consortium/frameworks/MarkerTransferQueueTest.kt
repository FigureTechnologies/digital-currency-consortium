package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.DEFAULT_AMOUNT
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.TEST_OTHER_MEMBER_ADDRESS
import io.provenance.digitalcurrency.consortium.api.BankSettlementRequest
import io.provenance.digitalcurrency.consortium.api.DepositFiatRequest
import io.provenance.digitalcurrency.consortium.bankclient.BankClient
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus.ERROR
import io.provenance.digitalcurrency.consortium.domain.TxStatus.TXN_COMPLETE
import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import io.provenance.digitalcurrency.consortium.messages.MemberListResponse
import io.provenance.digitalcurrency.consortium.messages.MemberResponse
import io.provenance.digitalcurrency.consortium.randomTxHash
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class MarkerTransferQueueTest : BaseIntegrationTest() {
    @Autowired
    lateinit var bankClientMock: BankClient

    @Autowired
    lateinit var pbcServiceMock: PbcService

    @Autowired
    private lateinit var coroutineProperties: CoroutineProperties

    @Autowired
    private lateinit var serviceProperties: ServiceProperties

    private lateinit var markerTransferQueue: MarkerTransferQueue

    @BeforeEach
    fun beforeAll() {
        reset(bankClientMock)
        reset(pbcServiceMock)

        whenever(pbcServiceMock.getMembers()).thenReturn(
            MemberListResponse(
                members = listOf(
                    MemberResponse(
                        id = TEST_OTHER_MEMBER_ADDRESS,
                        supply = 10000,
                        maxSupply = 10000000,
                        denom = "otherbank.omni.dcc",
                        joined = 12345,
                        weight = 10000000,
                        name = "Other Bank"
                    )
                )
            )
        )

        markerTransferQueue = MarkerTransferQueue(
            bankClient = bankClientMock,
            pbcService = pbcServiceMock,
            coroutineProperties = coroutineProperties
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
            denom = serviceProperties.dccDenom
        ).id.value

        markerTransferQueue.processMessage(MarkerTransferDirective(uuid))

        verify(bankClientMock).depositFiat(
            DepositFiatRequest(
                uuid = uuid,
                bankAccountUUID = bankAccountUuid,
                amount = DEFAULT_AMOUNT.toUSDAmount()
            )
        )
        verify(bankClientMock, never()).settleFiat(any())
    }

    @Test
    fun `marker transfer for member bank should notify bank of fiat settlement`() {
        val bankAccountUuid = UUID.randomUUID()
        transaction { insertRegisteredAddress(bankAccountUuid, TEST_ADDRESS, TXN_COMPLETE) }
        val uuid = insertMarkerTransfer(
            txHash = randomTxHash(),
            fromAddress = TEST_OTHER_MEMBER_ADDRESS,
            toAddress = TEST_MEMBER_ADDRESS,
            status = TXN_COMPLETE,
            denom = serviceProperties.dccDenom
        ).id.value

        markerTransferQueue.processMessage(MarkerTransferDirective(uuid))

        verify(bankClientMock).settleFiat(
            BankSettlementRequest(
                uuid = uuid,
                bankMemberAddress = TEST_OTHER_MEMBER_ADDRESS,
                bankMemberName = "Other Bank",
                amount = DEFAULT_AMOUNT.toUSDAmount()
            )
        )
        verify(bankClientMock, never()).depositFiat(any())
    }

    @Test
    fun `marker transfer for unknown from address will error`() {
        val uuid = insertMarkerTransfer(
            txHash = randomTxHash(),
            fromAddress = "somebadfromaddress",
            toAddress = TEST_MEMBER_ADDRESS,
            status = TXN_COMPLETE,
            denom = serviceProperties.dccDenom
        ).id.value

        markerTransferQueue.processMessage(MarkerTransferDirective(uuid))

        verify(bankClientMock, never()).settleFiat(any())
        verify(bankClientMock, never()).depositFiat(any())

        Assertions.assertEquals(ERROR, transaction { MarkerTransferRecord.findById(uuid)!!.status }, "Marker transfer marked as error")
    }
}
