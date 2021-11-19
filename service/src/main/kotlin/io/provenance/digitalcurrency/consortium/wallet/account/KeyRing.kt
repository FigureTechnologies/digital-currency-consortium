package io.provenance.digitalcurrency.consortium.wallet.account

import io.provenance.digitalcurrency.consortium.wallet.hdwallet.Account

interface KeyRing : Bip32Serializable {
    /**
     * A unique id that can be used to identify this key ring for caching.
     */
    fun id(): String
    fun key(index: Int, hardenAddress: Boolean): KeyI
    fun defaultKey(hardenAddress: Boolean): KeyI = key(0, hardenAddress)
}

abstract class BaseKeyRing(protected val root: Account) : KeyRing {
    companion object {
        private val lock = Object()
    }

    override fun id() = root.bech32Address()

    fun childAccount(index: Int, hardenAddress: Boolean): Account = synchronized(lock) {
        return root.childAccount(index, internalAddress = false, hardenAddress = hardenAddress, stripPrivateKey = false).also {
            if (it.type() != Account.AccountType.ADDRESS)
                throw IllegalArgumentException("Unable to use type ${root.type()}/${it.type()} as a KeyRing")
        }
    }
}

open class InMemoryKeyRing(root: Account) : BaseKeyRing(root) {
    companion object {
        private val lock = Object()
    }

    override fun key(index: Int, hardenAddress: Boolean): KeyI = InMemoryKey(childAccount(index, hardenAddress))

    override fun serialize(): String = synchronized(lock) {
        return root.serialize()
    }
}
