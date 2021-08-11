package io.provenance.digitalcurrency.consortium.wallet.signer

import com.google.protobuf.ByteString
import io.provenance.digitalcurrency.consortium.extension.toByteString
import io.provenance.digitalcurrency.consortium.wallet.hdwallet.encodeAsBTC
import io.provenance.digitalcurrency.consortium.wallet.util.Hash
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
