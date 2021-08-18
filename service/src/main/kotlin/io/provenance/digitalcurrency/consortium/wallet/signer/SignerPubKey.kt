package io.provenance.digitalcurrency.consortium.wallet.signer

import com.google.protobuf.ByteString
import io.provenance.digitalcurrency.consortium.wallet.account.Key
import io.provenance.digitalcurrency.consortium.wallet.hdwallet.Bech32
import io.provenance.digitalcurrency.consortium.wallet.hdwallet.encodeAsBTC
import io.provenance.digitalcurrency.consortium.wallet.hdwallet.toBech32Data
import io.provenance.digitalcurrency.consortium.wallet.signer.ProvenanceSigner.Companion.KeyType.EC
import io.provenance.digitalcurrency.consortium.wallet.util.ECUtils
import io.provenance.digitalcurrency.consortium.wallet.util.Hash
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.kethereum.crypto.impl.ec.EllipticCurveSigner
import org.kethereum.model.ECKeyPair
import java.security.KeyPair
import java.security.PublicKey
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

interface Signer {
    fun address(): String

    /**
     * Sign byte array.
     */
    fun sign(data: ByteArray): Signature

    fun signLambda(): (ByteArray) -> List<ByteString>
}

class ProvenanceSigner(val keypair: KeyPair, val ecKeyPair: ECKeyPair? = null, private val mainNet: Boolean) : Signer {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    companion object {
        enum class KeyType(val algo: String) {
            EC("SHA256withECDSA")
        }

        private fun keyType(key: java.security.Key) =
            when (key) {
                is ECPublicKey, is ECPrivateKey -> EC
                else -> throw UnsupportedOperationException("Key type not implemented")
            }

        fun asKey(key: PublicKey, mainNet: Boolean): Key {
            var keyBytes: ByteArray
            keyType(key).also { keyType ->
                when (keyType) {
                    EC -> {
                        keyBytes = (key as BCECPublicKey).q.getEncoded(true)
                    }
                }
            }

            return Key(
                address = getAddress(keyBytes, mainNet),
                encoded_key = keyBytes,
                curve = ECUtils.LEGACY_DIME_CURVE,
                encoding = "RAW"
            )
        }

        fun getAddress(key: PublicKey, mainNet: Boolean): String =
            getAddress(asKey(key, mainNet).encoded_key, mainNet)

        private fun getAddress(bytes: ByteArray, mainNet: Boolean) =
            bytes.let {
                (ECUtils.convertBytesToPublicKey(it) as BCECPublicKey).q.getEncoded(true)
            }.let {
                Hash.sha256hash160(it)
            }.let {
                mainNet.let {
                    if (it)
                        Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX
                    else
                        Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX
                }.let { prefix ->
                    it.toBech32Data(prefix).address
                }
            }
    }

    override fun address(): String = getAddress(keypair.public, mainNet)

    /**
     * Sign byte array.
     */
    override fun sign(data: ByteArray): Signature {
        require(ecKeyPair != null) { "Signer doesn't implement kethereum BigInteger keypair." }
        val signature = EllipticCurveSigner()
            .sign(Hash.sha256(data), ecKeyPair.privateKey.key, true)
            .encodeAsBTC()

        return Signature(
            public_key = asKey(keypair.public, mainNet),
            signature_bytes = signature
        )
    }

    override fun signLambda(): (ByteArray) -> List<ByteString> {
        require(ecKeyPair != null) { "Signer doesn't implement kethereum BigInteger keypair." }
        return PbSigner.signerFor(ecKeyPair)
    }
}

data class Signature(
    val public_key: Key,
    val signature_bytes: ByteArray
)
