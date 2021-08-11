package io.provenance.digitalcurrency.consortium.pbclient.api.grpc

import kotlin.math.ceil

private fun Double.roundUp(): Long = ceil(this).toLong()

data class GasEstimate(val estimate: Long, val feeAdjustment: Double? = DEFAULT_FEE_ADJUSTMENT) {
    companion object {
        private const val DEFAULT_FEE_ADJUSTMENT = 1.25
        private const val DEFAULT_GAS_PRICE = 1905.00
    }
    private val adjustment = feeAdjustment ?: DEFAULT_FEE_ADJUSTMENT

    val limit = (estimate * adjustment).roundUp()
    val fees = (limit * DEFAULT_GAS_PRICE).roundUp()
}
