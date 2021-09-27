package io.provenance.digitalcurrency.consortium.frameworks

import io.provenance.digitalcurrency.consortium.BaseIntegrationTest
import io.provenance.digitalcurrency.consortium.TEST_ADDRESS
import io.provenance.digitalcurrency.consortium.config.BalanceReportProperties
import io.provenance.digitalcurrency.consortium.config.CoroutineProperties
import io.provenance.digitalcurrency.consortium.config.ServiceProperties
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryRecord
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryTable
import io.provenance.digitalcurrency.consortium.domain.BalanceReportRecord
import io.provenance.digitalcurrency.consortium.service.PbcService
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class BalanceReportQueueTest : BaseIntegrationTest() {
    @Autowired
    lateinit var pbcServiceMock: PbcService

    @Autowired
    private lateinit var coroutineProperties: CoroutineProperties

    @Autowired
    private lateinit var serviceProperties: ServiceProperties

    @BeforeEach
    fun beforeAll() {
        reset(pbcServiceMock)
    }

    @Test
    fun `empty whitelist should not error`() {
        val balanceReportProperties = BalanceReportProperties(
            pageSize = "1",
            pollingDelayMs = "1000",
            addressesWhitelist = ""
        )
        val balanceReportQueue = BalanceReportQueue(
            balanceReportProperties = balanceReportProperties,
            serviceProperties = serviceProperties,
            coroutineProperties = coroutineProperties,
            pbcService = pbcServiceMock
        )

        val balanceReport: BalanceReportRecord = transaction {
            BalanceReportRecord.insert()
        }

        balanceReportQueue.processMessage(
            BalanceReportDirective(balanceReport.id.value)
        )

        verify(pbcServiceMock, never()).getCoinBalance(any(), any())

        transaction {
            Assertions.assertEquals(BalanceEntryTable.selectAll().count(), 0)
        }
    }

    @Test
    fun `one whitelist, no addresses should generate report`() {
        whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("500")

        val balanceReportProperties = BalanceReportProperties(
            pageSize = "1",
            pollingDelayMs = "1000",
            addressesWhitelist = TEST_ADDRESS
        )
        val balanceReportQueue = BalanceReportQueue(
            balanceReportProperties = balanceReportProperties,
            serviceProperties = serviceProperties,
            coroutineProperties = coroutineProperties,
            pbcService = pbcServiceMock
        )

        val balanceReport: BalanceReportRecord = transaction {
            BalanceReportRecord.insert()
        }

        balanceReportQueue.processMessage(
            BalanceReportDirective(balanceReport.id.value)
        )

        transaction {
            Assertions.assertEquals(
                BalanceEntryRecord.find {
                    (BalanceEntryTable.report eq balanceReport.id) and (BalanceEntryTable.address eq TEST_ADDRESS)
                }.count(),
                1
            )
        }
    }

    @Test
    fun `one whitelist, some addresses should generate report`() {
        whenever(pbcServiceMock.getCoinBalance(any(), any())).thenReturn("500")
        val balanceReportProperties = BalanceReportProperties(
            pageSize = "1",
            pollingDelayMs = "1000",
            addressesWhitelist = TEST_ADDRESS
        )
        val balanceReportQueue = BalanceReportQueue(
            balanceReportProperties = balanceReportProperties,
            serviceProperties = serviceProperties,
            coroutineProperties = coroutineProperties,
            pbcService = pbcServiceMock
        )

        transaction {
            AddressRegistrationRecord.insert(
                bankAccountUuid = UUID.randomUUID(),
                address = "dummyAddress1"
            )

            AddressRegistrationRecord.insert(
                bankAccountUuid = UUID.randomUUID(),
                address = "dummyAddress2"
            )
        }

        val balanceReport: BalanceReportRecord = transaction {
            BalanceReportRecord.insert()
        }

        balanceReportQueue.processMessage(
            BalanceReportDirective(balanceReport.id.value)
        )

        verify(pbcServiceMock, times(3)).getCoinBalance(any(), any())

        transaction {
            Assertions.assertEquals(
                BalanceEntryRecord.find {
                    (BalanceEntryTable.report eq balanceReport.id) and (BalanceEntryTable.address eq TEST_ADDRESS)
                }.count(),
                1
            )

            Assertions.assertEquals(
                BalanceEntryRecord.find {
                    (BalanceEntryTable.report eq balanceReport.id) and (BalanceEntryTable.address eq "dummyAddress1")
                }.count(),
                1
            )

            Assertions.assertEquals(
                BalanceEntryRecord.find {
                    (BalanceEntryTable.report eq balanceReport.id) and (BalanceEntryTable.address eq "dummyAddress2")
                }.count(),
                1
            )

            Assertions.assertEquals(
                BalanceEntryRecord.find {
                    (BalanceEntryTable.report eq balanceReport.id)
                }.count(),
                3
            )
        }
    }
}
