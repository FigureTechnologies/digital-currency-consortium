package io.provenance.digitalcurrency.consortium.wallet.account

// interface KeyRing : Bip32Serializable {
//     /**
//      * A unique id that can be used to identify this key ring for caching.
//      */
//     fun id(): String
//     fun key(index: Int): Key
//     fun defaultKey(): Key = key(0)
// }
//
// abstract class BaseKeyRing(protected val root: Account) : KeyRing {
//     companion object {
//         private val lock = Object()
//     }
//
//     override fun id() = root.bech32Address()
//
//     fun childAccount(index: Int): Account = synchronized(lock) {
//         return root.childAccount(index, internalAddress = false, hardenAddress = true, stripPrivateKey = false).also {
//             if (it.type() != Account.AccountType.ADDRESS)
//                 throw IllegalArgumentException("Unable to use type ${root.type()}/${it.type()} as a KeyRing")
//         }
//     }
// }
//
// open class InMemoryKeyRing(root: Account) : BaseKeyRing(root) {
//     companion object {
//         private val lock = Object()
//     }
//
//     override fun key(index: Int): Key = InMemoryKey(childAccount(index))
//
//     override fun serialize(): String = synchronized(lock) {
//         return root.serialize()
//     }
// }
