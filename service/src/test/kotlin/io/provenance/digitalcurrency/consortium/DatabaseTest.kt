package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.domain.ADT
import io.provenance.digitalcurrency.consortium.domain.ART
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressStatus
import io.provenance.digitalcurrency.consortium.domain.AddressStatus.INSERTED
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryTable
import io.provenance.digitalcurrency.consortium.domain.BalanceReportTable
import io.provenance.digitalcurrency.consortium.domain.CBT
import io.provenance.digitalcurrency.consortium.domain.CMT
import io.provenance.digitalcurrency.consortium.domain.CRT
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementTable
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus
import io.provenance.digitalcurrency.consortium.domain.MINT
import io.provenance.digitalcurrency.consortium.domain.MTT
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferStatus
import io.provenance.digitalcurrency.consortium.domain.MigrationRecord
import io.provenance.digitalcurrency.consortium.domain.TST
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import java.time.OffsetDateTime
import java.util.UUID

abstract class DatabaseTest {
    @AfterEach
    fun afterEach() {
        transaction {
            ADT.deleteAll()
            CBT.deleteAll()
            CMT.deleteAll()
            CoinMovementTable.deleteAll()
            CRT.deleteAll()
            TST.deleteAll()
            ART.deleteAll()
            MTT.deleteAll()
            BalanceEntryTable.deleteAll()
            BalanceReportTable.deleteAll()
        }
    }

    fun insertRegisteredAddress(bankAccountUuid: UUID, address: String, status: AddressStatus = INSERTED, txHash: String? = null) =
        AddressRegistrationRecord.insert(
            uuid = UUID.randomUUID(),
            bankAccountUuid = bankAccountUuid,
            address = address
        ).apply {
            this.status = status
            this.txHash = txHash
        }

    fun insertDeregisteredAddress(
        addressRegistrationRecord: AddressRegistrationRecord,
        status: AddressStatus = INSERTED,
        txHash: String? = null
    ) =
        AddressDeregistrationRecord.insert(addressRegistrationRecord).apply {
            this.status = status
            this.txHash = txHash
        }

    fun insertCoinRedemption(status: CoinRedemptionStatus) = transaction {
        CoinRedemptionRecord.insert(
            coinAmount = DEFAULT_AMOUNT.toLong(),
            addressRegistration = insertRegisteredAddress(UUID.randomUUID(), TEST_ADDRESS)
        ).also { it.status = status }
    }

    fun insertTxStatus(
        txRequestUuid: UUID,
        txHash: String,
        txType: TxType,
        txStatus: TxStatus,
        created: OffsetDateTime? = OffsetDateTime.now()
    ): TxStatusRecord =
        transaction {
            TxStatusRecord.insert(getDefaultResponse(txHash).txResponse, txRequestUuid, txType).also {
                it.status = txStatus
                it.created = created!!
            }
        }

    fun insertMigration(
        txHash: String
    ): MigrationRecord =
        transaction {
            MigrationRecord.insert(
                codeId = "2",
                txHash = txHash
            )
        }

    fun insertMarkerTransfer(
        txHash: String,
        toAddress: String = TEST_ADDRESS,
        denom: String
    ): MarkerTransferRecord =
        transaction {
            MarkerTransferRecord.new(UUID.randomUUID()) {
                this.fromAddress = TEST_ADDRESS
                this.toAddress = toAddress
                this.denom = denom
                this.coinAmount = DEFAULT_AMOUNT.toLong()
                this.fiatAmount = DEFAULT_AMOUNT.toUSDAmount()
                this.height = 50L
                this.txHash = txHash
                this.status = MarkerTransferStatus.INSERTED
                this.created = OffsetDateTime.now()
                this.updated = OffsetDateTime.now()
            }
        }

    fun insertCoinMovement(txHash: String, denom: String, type: String = MINT) =
        transaction {
            CoinMovementRecord.insert(
                txHash = txHash,
                fromAddress = "fromAddress",
                toAddress = "toAddress",
                blockHeight = 50,
                amount = DEFAULT_AMOUNT.toString(),
                blockTime = OffsetDateTime.now(),
                denom = denom,
                type = type
            )
        }
}
