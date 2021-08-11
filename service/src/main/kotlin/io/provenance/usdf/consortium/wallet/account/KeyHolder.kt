package io.provenance.usdf.consortium.wallet.account

import io.provenance.usdf.consortium.wallet.hdwallet.Account
import io.provenance.usdf.consortium.wallet.hdwallet.WORDLIST_ENGLISH
import org.kethereum.bip39.model.MnemonicWords
import org.kethereum.bip39.toSeed

interface KeyHolder : Bip32Serializable {
    // fun keyring(index: Int): KeyRing
    // fun keyRing(index: Int) = keyring(index)
    // fun defaultKeyRing() = keyring(0)
}

abstract class BaseKeyHolder(protected val root: Account) : KeyHolder {
    protected fun childAccount(index: Int): Account {
        return root.childAccount(index, internalAddress = false, hardenAddress = true, stripPrivateKey = false).also {
            if (it.type() != Account.AccountType.GENERAL)
                throw IllegalArgumentException("Unable to use type ${root.type()}/${it.type()} as a Key")
        }
    }
}

open class InMemoryKeyHolder(root: Account) : BaseKeyHolder(root) {
    companion object {
        /**
         * Generate a 24 word mnemonic to be used as a seed for a new [ProvenanceWallet]
         */
        private fun generateMnemonic(strength: Int) =
            org.kethereum.bip39.generateMnemonic(strength * 32, WORDLIST_ENGLISH)

        /**
         * Create a new wallet from a generated mnemonic.
         *
         * @return Pair<String, ProvenanceWallet> - mnemonic, wallet
         */
        @JvmOverloads
        fun genesis(strength: Int = 8, mainNet: Boolean = false) =
            generateMnemonic(strength).let {
                Pair(it, fromMnemonic(it, mainNet))
            }

        /**
         * Restore a wallet from a previously generated seed mnemonic.
         */
        fun fromMnemonic(mnemonic: String, mainNet: Boolean = false) =
            InMemoryKeyHolder(
                Account(
                    MnemonicWords(mnemonic).toSeed(),
                    if (mainNet) Account.PROVENANCE_MAINNET_BIP44_PATH else Account.PROVENANCE_TESTNET_BIP44_PATH,
                    mainNet
                )
            )
    }

    // override fun keyring(index: Int): KeyRing = InMemoryKeyRing(childAccount(index))
    override fun serialize() = root.serialize()
}
