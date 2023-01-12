package io.opencola.core.serialization

import java.nio.ByteBuffer

object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
    // TODO: Can these be bytes instead?
    private const val FALSE = 0
    private const val TRUE = 1

    override fun encode(value: Boolean): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(if (value) TRUE else FALSE).array()
    }

    override fun decode(value: ByteArray): Boolean {
        return ByteBuffer.wrap(value).int == TRUE
    }
}