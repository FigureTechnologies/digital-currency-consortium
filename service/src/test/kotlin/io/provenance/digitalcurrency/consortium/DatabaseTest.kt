package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.domain.ADT
import io.provenance.digitalcurrency.consortium.domain.ART
import io.provenance.digitalcurrency.consortium.domain.AddressDeregistrationRecord
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.BalanceEntryTable
import io.provenance.digitalcurrency.consortium.domain.BalanceReportTable
import io.provenance.digitalcurrency.consortium.domain.CBT
import io.provenance.digitalcurrency.consortium.domain.CMT
import io.provenance.digitalcurrency.consortium.domain.CTT
import io.provenance.digitalcurrency.consortium.domain.CoinMintRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
import io.provenance.digitalcurrency.consortium.domain.CoinMovementTable
import io.provenance.digitalcurrency.consortium.domain.MINT
import io.provenance.digitalcurrency.consortium.domain.MTT
import io.provenance.digitalcurrency.consortium.domain.MarkerTransferRecord
import io.provenance.digitalcurrency.consortium.domain.MigrationRecord
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.extension.toUSDAmount
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

abstract class DatabaseTest {
    @AfterEach
    fun afterEach() {
        transaction {
            CTT.deleteAll()
            ADT.deleteAll()
            CBT.deleteAll()
            CMT.deleteAll()
            CoinMovementTable.deleteAll()
            ART.deleteAll()
            MTT.deleteAll()
            BalanceEntryTable.deleteAll()
            BalanceReportTable.deleteAll()
        }
    }

    fun insertRegisteredAddress(
        bankAccountUuid: UUID,
        address: String,
        status: TxStatus = TxStatus.QUEUED,
        txHash: String? = null
    ) =
        AddressRegistrationRecord.insert(
            bankAccountUuid = bankAccountUuid,
            address = address
        ).apply {
            this.status = status
            this.txHash = txHash
        }

    fun insertDeregisteredAddress(
        addressRegistrationRecord: AddressRegistrationRecord,
        status: TxStatus = TxStatus.QUEUED,
        txHash: String? = null
    ) =
        AddressDeregistrationRecord.insert(addressRegistrationRecord).apply {
            this.status = status
            this.txHash = txHash
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
        fromAddress: String = TEST_ADDRESS,
        toAddress: String = TEST_MEMBER_ADDRESS,
        status: TxStatus = TxStatus.QUEUED,
        denom: String
    ): MarkerTransferRecord =
        transaction {
            MarkerTransferRecord.new(UUID.randomUUID()) {
                this.fromAddress = fromAddress
                this.toAddress = toAddress
                this.denom = denom
                this.coinAmount = DEFAULT_AMOUNT.toLong()
                this.fiatAmount = DEFAULT_AMOUNT.toUSDAmount()
                this.height = 50L
                this.txHash = txHash
                this.status = status
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

    fun insertCoinMint(uuid: UUID = UUID.randomUUID(), address: String = "testaddress"): CoinMintRecord =
        insertRegisteredAddress(
            bankAccountUuid = uuid,
            address = address
        ).let {
            CoinMintRecord.insert(UUID.randomUUID(), addressRegistration = it, fiatAmount = BigDecimal("1000"))
        }
}
