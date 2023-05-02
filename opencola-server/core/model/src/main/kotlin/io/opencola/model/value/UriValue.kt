package io.opencola.model.value

import io.opencola.serialization.codecs.UriByteArrayCodec
import io.opencola.model.protobuf.Model as ProtoModel
import java.net.URI

class UriValue(value: URI) : Value<URI>(value) {
    companion object : ValueWrapper<URI> {
        override fun encode(value: URI): ByteArray {
            return UriByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): URI {
            return UriByteArrayCodec.decode(value)
        }

        override fun toProto(value: URI): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(io.opencola.model.ValueType.URI.ordinal)
                .setString(value.toString())
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): URI {
            require(value.ocType == io.opencola.model.ValueType.URI.ordinal)
            return URI.create(value.string)
        }

        override fun wrap(value: URI): Value<URI> {
            return UriValue(value)
        }

        override fun unwrap(value: Value<URI>): URI {
            require(value is UriValue)
            return value.get()
        }
    }

    override fun compareTo(other: Value<URI>): Int {
        if(other !is UriValue) return -1
        return value.compareTo(other.value)
    }
}