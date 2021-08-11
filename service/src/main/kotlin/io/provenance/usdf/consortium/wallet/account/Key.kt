package io.provenance.usdf.consortium.wallet.account

// import com.google.protobuf.ByteString
// import io.p8e.crypto.proto.CryptoProtos.Address
// import io.p8e.crypto.proto.CryptoProtos.Signature
// import io.provenance.core.encryption.ecies.ECUtils
// import io.provenance.usdf.consortium.wallet.hdwallet.Account
// import io.provenance.usdf.consortium.wallet.signer.ProvenanceSigner
// import io.provenance.usdf.consortium.wallet.signer.Signer
// import okio.ByteString.Companion.toByteString
// import java.security.KeyPair
//
// interface Key : Bip32Serializable, Signer {
//     fun publicKey(): ByteString
// }
//
// open class InMemoryKey(private val root: Account) : Key {
//     override fun publicKey(): ByteString = Account.compressedPublicKey(root.getECKeyPair().publicKey.key).toByteString()
//
// override fun serialize(): String = root.serialize()
//
// override fun address(): Address = ProvenanceSigner.getAddress(getKeyPair().public, root.isMainnet())
//
// private fun getKeyPair(): KeyPair =
//     root.getECKeyPair().let {
//         val privKey = ECUtils.convertBytesToPrivateKey(it.privateKey.key.toByteArray())
//         KeyPair(ECUtils.toPublicKey(privKey), privKey)
//     }
//
// override fun sign(data: ByteArray): Signature =
//     getKeyPair().let {
//         ProvenanceSigner(it, root.getECKeyPair(), root.isMainnet()).sign(data)
//     }
//
// override fun signLambda(): (ByteArray) -> List<ByteString> =
//     getKeyPair().let {
//         ProvenanceSigner(it, root.getECKeyPair(), root.isMainnet()).signLambda()
//     }
// }
