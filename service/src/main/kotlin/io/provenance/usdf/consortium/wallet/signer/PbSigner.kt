package io.provenance.usdf.consortium.wallet.signer

import io.provenance.usdf.consortium.wallet.util.Hash
import com.google.protobuf.ByteString
import io.provenance.usdf.consortium.extension.toByteString
import io.provenance.usdf.consortium.wallet.hdwallet.encodeAsBTC
import org.kethereum.crypto.impl.ec.EllipticCurveSigner
import org.kethereum.model.ECKeyPair

object PbSigner {
    fun signerFor(keyPair: ECKeyPair): (ByteArray) -> List<ByteString> = { bytes ->
        bytes.let {
            Hash.sha256(it)
        }.let {
            EllipticCurveSigner().sign(it, keyPair.privateKey.key, true).encodeAsBTC().toByteString()
        }.let {
            listOf(it)
        }
    }
}
