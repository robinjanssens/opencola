package opencola.core.model

import java.io.InputStream
import java.io.OutputStream

interface ByteArrayStreamCodec<T> {
    // TODO: Should this return an OutputStream? Fluid, but not consistent with decode
    fun encode(stream: OutputStream, value: T): OutputStream
    fun decode(stream: InputStream): T
}