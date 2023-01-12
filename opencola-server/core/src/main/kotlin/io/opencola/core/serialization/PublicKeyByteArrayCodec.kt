package io.opencola.core.serialization

import io.opencola.core.security.publicKeyFromBytes
import java.security.PublicKey

object PublicKeyByteArrayCodec : ByteArrayCodec<PublicKey> {
    override fun encode(value: PublicKey): ByteArray {
        return value.encoded
    }

    override fun decode(value: ByteArray): PublicKey {
        return publicKeyFromBytes(value)
    }
}