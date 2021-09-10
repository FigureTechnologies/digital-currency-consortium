package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.domain.ART
import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import io.provenance.digitalcurrency.consortium.domain.CMT
import io.provenance.digitalcurrency.consortium.domain.CRT
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionRecord
import io.provenance.digitalcurrency.consortium.domain.CoinRedemptionStatus
import io.provenance.digitalcurrency.consortium.domain.TST
import io.provenance.digitalcurrency.consortium.domain.TxStatus
import io.provenance.digitalcurrency.consortium.domain.TxStatusRecord
import io.provenance.digitalcurrency.consortium.domain.TxType
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import java.time.OffsetDateTime
import java.util.UUID

abstract class DatabaseTest {
    @AfterEach
    fun afterEach() {
        transaction {
            CMT.deleteAll()
            CRT.deleteAll()
            TST.deleteAll()
            ART.deleteAll()
        }
    }

    fun insertRegisteredAddress(bankAccountUuid: UUID, address: String) =
        AddressRegistrationRecord.insert(
            uuid = UUID.randomUUID(),
            bankAccountUuid = bankAccountUuid,
            address = address
        )

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
}
