package io.provenance.digitalcurrency.consortium.wallet.account

import com.google.protobuf.ByteString
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.wallet.hdwallet.Account
import io.provenance.digitalcurrency.consortium.wallet.signer.ProvenanceSigner
import io.provenance.digitalcurrency.consortium.wallet.signer.Signature
import io.provenance.digitalcurrency.consortium.wallet.signer.Signer
import io.provenance.digitalcurrency.consortium.wallet.util.ECUtils
import java.security.KeyPair

interface KeyI : Bip32Serializable, Signer {
    fun publicKey(): ByteString
}

open class InMemoryKey(private val root: Account) : KeyI {
    override fun publicKey(): ByteString = Account.compressedPublicKey(root.getECKeyPair().publicKey.key).toByteString()

    override fun serialize(): String = root.serialize()

    override fun address(): String = ProvenanceSigner.getAddress(getKeyPair().public, root.isMainnet())

    private fun getKeyPair(): KeyPair =
        root.getECKeyPair().let {
            val privKey = ECUtils.convertBytesToPrivateKey(it.privateKey.key.toByteArray())
            KeyPair(ECUtils.toPublicKey(privKey), privKey)
        }

    override fun sign(data: ByteArray): Signature =
        getKeyPair().let {
            ProvenanceSigner(it, root.getECKeyPair(), root.isMainnet()).sign(data)
        }

    override fun signLambda(): (ByteArray) -> List<ByteString> =
        getKeyPair().let {
            ProvenanceSigner(it, root.getECKeyPair(), root.isMainnet()).signLambda()
        }
}

data class Key(
    val address: String,
    val encoded_key: ByteArray,
    val curve: String, // prime256v1, ed25519
    val encoding: String // PEM/DER
)
