package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.ValueType
import io.opencola.model.protobuf.Model
import io.opencola.security.PublicKeyByteArrayCodec
import io.opencola.util.compareTo
import java.security.PublicKey

class PublicKeyValue(value: PublicKey) : Value<PublicKey>(value) {
    companion object : ValueWrapper<PublicKey> {
        override fun encode(value: PublicKey): ByteArray {
            return PublicKeyByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): PublicKey {
            return PublicKeyByteArrayCodec.decode(value)
        }

        override fun toProto(value: PublicKey): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(ValueType.PUBLIC_KEY.ordinal)
                .setBytes(ByteString.copyFrom(encode(value)))
                .build()
        }

        override fun fromProto(value: Model.Value): PublicKey {
            require(value.ocType == ValueType.PUBLIC_KEY.ordinal)
            return decode(value.bytes.toByteArray())
        }

        override fun wrap(value: PublicKey): Value<PublicKey> {
            return PublicKeyValue(value)
        }

        override fun unwrap(value: Value<PublicKey>): PublicKey {
            require(value is PublicKeyValue)
            return value.get()
        }
    }

    override fun compareTo(other: Value<PublicKey>): Int {
        if(other !is PublicKeyValue) return -1
        return this.value.encoded.compareTo(other.value.encoded)
    }
}