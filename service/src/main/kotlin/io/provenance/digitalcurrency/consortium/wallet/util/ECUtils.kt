package io.provenance.digitalcurrency.consortium.wallet.util

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.spec.InvalidKeySpecException
import kotlin.experimental.and

object ECUtils {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // Legacy DIME encryption curve.  P-256 curve is the one to use going forward.
    val LEGACY_DIME_CURVE = "secp256k1"
    val provider = "BC"

    /**
     * Convert a byte array to an EC private key by decoding the D number
     * parameter.
     *
     * @param keyBytes Bytes to be converted to the EC private key.
     * @return An instance of EC private key decoded from the input bytes.
     * @throws InvalidKeySpecException The provided key bytes are not a valid EC
     * private key.
     */
    @Throws(InvalidKeySpecException::class)
    fun convertBytesToPrivateKey(keyBytes: ByteArray, curve: String = LEGACY_DIME_CURVE): PrivateKey {
        try {

            val kf = KeyFactory.getInstance("ECDH", provider)
            val keyInteger = unsignedBytesToBigInt(keyBytes)
            val ecSpec = ECNamedCurveTable.getParameterSpec(curve)
                ?: throw InvalidKeySpecException("Could not get parameter spec for '$curve': ensure crypto provider is correctly inialized")
            val keySpec = ECPrivateKeySpec(keyInteger, ecSpec)

            return kf.generatePrivate(keySpec)
        } catch (ex: Exception) {
            throw InvalidKeySpecException(ex.message, ex)
        }
    }

    @Throws(InvalidKeySpecException::class)
    fun convertBytesToPublicKey(keyBytes: ByteArray, curve: String = LEGACY_DIME_CURVE): PublicKey {
        try {
            val kf = KeyFactory.getInstance("ECDH", provider)

            val ecSpec = ECNamedCurveTable.getParameterSpec(curve)
                ?: throw InvalidKeySpecException("Could not get parameter spec for '$curve': ensure crypto provider is correctly inialized")
            val point = ecSpec.curve.decodePoint(keyBytes)
            val pubSpec = ECPublicKeySpec(point, ecSpec)

            return kf.generatePublic(pubSpec)
        } catch (ex: Exception) {
            throw InvalidKeySpecException(ex.message, ex)
        }
    }

    fun toPublicKey(privateKey: PrivateKey, curve: String = LEGACY_DIME_CURVE): PublicKey? {
        val kf = KeyFactory.getInstance("ECDH", provider)

        val ecSpec = ECNamedCurveTable.getParameterSpec(curve)
            ?: throw InvalidKeySpecException("Could not get parameter spec for '$curve': ensure crypto provider is correctly inialized")

        val Q: ECPoint =
            ecSpec.g.multiply((privateKey as ECPrivateKey).d)

        val pubSpec = ECPublicKeySpec(Q, ecSpec)
        return kf.generatePublic(pubSpec)
    }

    private fun unsignedBytesToBigInt(bytes: ByteArray): BigInteger {
        var result: BigInteger = BigInteger.ZERO
        for (i in 1..bytes.size) {
            val value: Long = (bytes[bytes.size - i] and 0xFF.toByte()).toUByte().toLong()
            result = result.add(BigInteger.valueOf(value).shiftLeft((i - 1) * 8))
        }
        return result
    }
}
