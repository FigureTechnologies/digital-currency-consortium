package io.provenance.digitalcurrency.consortium.domain

import io.provenance.digitalcurrency.consortium.extension.toCoinAmount
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

typealias CMT = CoinMintTable

object CoinMintTable : BaseRequestTable(name = "coin_mint") {
    val addressRegistration = reference("address_registration_uuid", AddressRegistrationTable)
    val status = enumerationByName("status", 15, CoinMintStatus::class)
}

open class CoinMintEntityClass : BaseRequestEntityClass<CMT, CoinMintRecord>(CMT) {
    fun insert(
        uuid: UUID,
        addressRegistration: AddressRegistrationRecord,
        fiatAmount: BigDecimal
    ) = new(uuid) {
        this.addressRegistration = addressRegistration
        this.fiatAmount = fiatAmount
        this.coinAmount = fiatAmount.toCoinAmount().toLong()
        this.status = CoinMintStatus.INSERTED
        this.created = OffsetDateTime.now()
        this.updated = OffsetDateTime.now()
    }

    fun updateStatus(uuid: UUID, newStatus: CoinMintStatus) =
        findById(uuid)!!.let {
            it.status = newStatus
            it.updated = OffsetDateTime.now()
        }

    fun updateBatchStatus(uuids: List<UUID>, newStatus: CoinMintStatus) =
        BatchUpdateStatement(CMT).apply {
            uuids.forEach { uuid ->
                addBatch(id = EntityID(uuid, CMT))
                this[CMT.status] = newStatus
                this[CMT.updated] = OffsetDateTime.now()
            }
            execute(TransactionManager.current())
        }

    fun findNew(batchSize: Int) =
        find { CMT.status eq CoinMintStatus.INSERTED }.orderBy(CMT.created to SortOrder.ASC).limit(batchSize)

    fun findPending() = find { CMT.status eq CoinMintStatus.PENDING_MINT }

    fun findForUpdate(uuid: UUID) = find { CMT.id eq uuid }.forUpdate()

    fun findInsertedInListForUpdate(uuids: List<UUID>) =
        find { (CMT.id inList uuids) and (CMT.status eq CoinMintStatus.INSERTED) }.forUpdate().toList()
}

class CoinMintRecord(uuid: EntityID<UUID>) : BaseRequestRecord(CMT, uuid) {
    companion object : CoinMintEntityClass()

    var addressRegistration by AddressRegistrationRecord referencedOn CMT.addressRegistration
    var status by CMT.status
}

enum class CoinMintStatus {
    INSERTED,
    PENDING_MINT,
    COMPLETE,
    EXCEPTION
}
