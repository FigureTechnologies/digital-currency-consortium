package io.provenance.usdf.consortium.wallet.account

interface Bip32Serializable {
    /**
     * Serialize the wallet to a bip32 string representation.
     */
    fun serialize(): String
}
