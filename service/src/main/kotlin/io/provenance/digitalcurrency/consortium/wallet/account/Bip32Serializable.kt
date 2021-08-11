package io.provenance.digitalcurrency.consortium.wallet.account

interface Bip32Serializable {
    /**
     * Serialize the wallet to a bip32 string representation.
     */
    fun serialize(): String
}
