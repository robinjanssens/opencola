/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.common.message.v2

import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.protobuf.Relay
import io.opencola.security.SignedBytes
import io.opencola.security.generateAesKey
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PrivateKey
import java.security.PublicKey

class PayloadEnvelope(val header: SignedBytes, val message: SignedBytes) {
    override fun toString(): String {
        return "EnvelopeV2(header=ENCRYPTED, message=ENCRYPTED)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PayloadEnvelope) return false

        if (header != other.header) return false
        return message == other.message
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<PayloadEnvelope, Proto.Envelope> {
        override fun toProto(value: PayloadEnvelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setHeader(value.header.toProto())
                .setMessage(value.message.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Envelope): PayloadEnvelope {
            return PayloadEnvelope(
                SignedBytes.fromProto(value.header),
                SignedBytes.fromProto(value.message)
            )
        }

        override fun parseProto(bytes: ByteArray): Relay.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }

        fun encodePayload(
            from: PrivateKey,
            headerTo: PublicKey,
            messageTo: List<PublicKey>,
            messageStorageKey: MessageStorageKey,
            message: Message
        ): ByteArray {
            val messageSecretKey = generateAesKey()
            val signedEncryptedHeader =
                EnvelopeHeader(messageTo, messageStorageKey, messageSecretKey).encryptAndSign(from, headerTo)
            val signedEncryptedMessage = message.encryptAndSign(from, messageSecretKey)
            return PayloadEnvelope(signedEncryptedHeader, signedEncryptedMessage).encodeProto()
        }

        fun decodePayload(to: PrivateKey, from: PublicKey, payload: ByteArray): Envelope {
            return PayloadEnvelope.decodeProto(payload)
                .let { EnvelopeHeader.decryptAndVerifySignature(to, from, it.header) }
                .let { Envelope(it.recipients, it.messageStorageKey, PayloadEnvelope.decodeProto(payload).message) }
        }

        fun from(from: PrivateKey, to: PublicKey, envelope: Envelope): PayloadEnvelope {
            // An envelope may contain multiple recipients. Here, we are preparing the payload for a single recipient.
            val recipient = envelope.recipients.single { it.publicKey == to }
            val encryptedHeader = EnvelopeHeader(recipient, envelope.messageStorageKey).encryptAndSign(from, to)
            return PayloadEnvelope(encryptedHeader, envelope.message)
        }
    }
}