package io.provenance.digitalcurrency.consortium.domain

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager

class InsertUpdateOnConflictDoNothing<T : Any>(
    private val constraint: String?,
    table: Table,
    isIgnore: Boolean = false // does nothing on postgres dialect
) : InsertStatement<T>(table, isIgnore) {

    override fun prepareSQL(transaction: Transaction): String {
        val conflict = "ON CONFLICT ${constraint?.let { "ON CONSTRAINT \"$it\"" }} DO NOTHING"
        return "${super.prepareSQL(transaction)} $conflict"
    }
}

fun <T : Table> T.upsertDoNothing(
    constraint: String?,
    body: T.(InsertStatement<ResultRow>) -> Unit
) = InsertUpdateOnConflictDoNothing<ResultRow>(constraint, this).apply {
    body(this)
    execute(TransactionManager.current())
}
