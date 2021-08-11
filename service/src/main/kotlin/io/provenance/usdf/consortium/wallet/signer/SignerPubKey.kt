package io.provenance.usdf.consortium.wallet.signer

// import io.provenance.usdf.consortium.wallet.hdwallet.Bech32
// import io.provenance.usdf.consortium.wallet.signer.ProvenanceSigner.Companion.KeyType.EC
// import io.provenance.usdf.consortium.wallet.util.Hash
// import com.google.protobuf.ByteString
// import io.p8e.crypto.proto.CryptoProtos.Address
// import io.p8e.crypto.proto.CryptoProtos.AddressType.BECH32
// import io.p8e.crypto.proto.CryptoProtos.Key
// import io.p8e.crypto.proto.CryptoProtos.Signature
// import io.provenance.core.encryption.ecies.ECUtils
// import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
// import org.bouncycastle.jce.provider.BouncyCastleProvider
// import org.kethereum.crypto.impl.ec.EllipticCurveSigner
// import org.kethereum.model.ECKeyPair
// import java.security.KeyPair
// import java.security.PublicKey
// import java.security.Security
// import java.security.interfaces.ECPrivateKey
// import java.security.interfaces.ECPublicKey
//
// interface Signer {
//     fun address(): Address
//
//     /**
//      * Sign byte array.
//      */
//     fun sign(data: ByteArray): Signature
//
//     fun signLambda(): (ByteArray) -> List<ByteString>
// }
//
// class ProvenanceSigner(val keypair: KeyPair, val ecKeyPair: ECKeyPair? = null, private val mainNet: Boolean) : Signer {
//     init {
//         Security.addProvider(BouncyCastleProvider())
//     }
//
//     companion object {
//         enum class KeyType(val algo: String) {
//             EC("SHA256withECDSA")
//         }
//
//         private fun keyType(key: java.security.Key) =
//             when (key) {
//                 is ECPublicKey, is ECPrivateKey -> EC
//                 else -> throw UnsupportedOperationException("Key type not implemented")
//             }
//
//         fun asKey(key: PublicKey, mainNet: Boolean): Key =
//             Key.newBuilder().also {
//                 var keyBytes: ByteArray
//                 keyType(key).also { keyType ->
//                     when (keyType) {
//                         EC -> {
//                             keyBytes = (key as BCECPublicKey).q.getEncoded(true)
//                             it.curve = ECUtils.LEGACY_DIME_CURVE
//                         }
//                     }
//                 }
//
//                 it.encodedKey = keyBytes.toByteString()
//                 it.address = getAddress(keyBytes, mainNet)
//                 it.encoding = "RAW"
//             }.build()
//
//         fun getAddress(key: PublicKey, mainNet: Boolean): Address =
//             getAddress(asKey(key, mainNet).encodedKey.toByteArray(), mainNet)
//
//         private fun getAddress(bytes: ByteArray, mainNet: Boolean) =
//             bytes.let {
//                 (ECUtils.convertBytesToPublicKey(it) as BCECPublicKey).q.getEncoded(true)
//             }.let {
//                 Hash.sha256hash160(it)
//             }.let {
//                 mainNet.let {
//                     if (it)
//                         Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX
//                     else
//                         Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX
//                 }.let { prefix ->
//                     it.toBech32Data(prefix).address
//                 }
//             }.let {
//                 Address.newBuilder().setValue(it).setType(BECH32).build()
//             }
//     }
//
//     override fun address(): Address = getAddress(keypair.public, mainNet)
//
//     /**
//      * Sign byte array.
//      */
//     override fun sign(data: ByteArray): Signature {
//         require(ecKeyPair != null) { "Signer doesn't implement kethereum BigInteger keypair." }
//         val signature = EllipticCurveSigner()
//             .sign(Hash.sha256(data), ecKeyPair.privateKey.key, true)
//             .encodeAsBTC()
//             .toByteString()
//
//         return Signature.newBuilder()
//             .setPublicKey(asKey(keypair.public, mainNet))
//             .setSignatureBytes(signature)
//             .build()
//     }
//
//     override fun signLambda(): (ByteArray) -> List<ByteString> {
//         require(ecKeyPair != null) { "Signer doesn't implement kethereum BigInteger keypair." }
//         return PbSigner.signerFor(ecKeyPair)
//     }
// }
