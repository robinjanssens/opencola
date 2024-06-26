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

package io.opencola.network

import io.opencola.model.Id
import io.opencola.security.PublicKeyByteArrayCodec
import io.opencola.security.Signator
import io.opencola.security.isValidSignature
import io.opencola.serialization.*
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.util.Base58
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.PublicKey
import java.time.Instant

val emptyByteArray = "".toByteArray()

data class InviteToken(
    val authorityId: Id,
    val name: String,
    val publicKey: PublicKey,
    val address: URI,
    val imageUri: URI,
    val tokenId: Id = Id.new(),
    val epochSecond: Long = Instant.now().epochSecond,
    val body: ByteArray = emptyByteArray
) {

    fun encode(signator: Signator): ByteArray {
        val token = ByteArrayOutputStream().use {
            Id.encode(it, authorityId)
            it.writeString(name)
            it.writeByteArray(PublicKeyByteArrayCodec.encode(publicKey))
            it.writeUri(address)
            it.writeUri(imageUri)
            Id.encode(it, tokenId)
            it.writeLong(epochSecond)
            it.writeByteArray(body)
            it.toByteArray()
        }
        val signature = signator.signBytes(authorityId.toString(), token)
        val signedToken = ByteArrayOutputStream().use {
            it.writeByteArray(token)
            it.writeByteArray(signature.bytes)
            it.toByteArray()
        }

        return signedToken
    }

    fun encodeBase58(signator: Signator): String {
        return Base58.encode(encode(signator))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InviteToken

        if (authorityId != other.authorityId) return false
        if (name != other.name) return false
        if (publicKey != other.publicKey) return false
        if (address != other.address) return false
        if (imageUri != other.imageUri) return false
        if (tokenId != other.tokenId) return false
        if (epochSecond != other.epochSecond) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authorityId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + imageUri.hashCode()
        result = 31 * result + tokenId.hashCode()
        result = 31 * result + epochSecond.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object Factory {
        private fun toInviteToken(body: ByteArray): InviteToken {
            return ByteArrayInputStream(body).use {
                InviteToken(
                    Id.decode(it),
                    it.readString(),
                    PublicKeyByteArrayCodec.decode(it.readByteArray()),
                    it.readUri(),
                    it.readUri(),
                    Id.decode(it),
                    it.readLong(),
                    it.readByteArray(),
                )
            }
        }

        fun fromAddressBookEntry(addressBookEntry: AddressBookEntry): InviteToken {
            return InviteToken(
                addressBookEntry.personaId,
                addressBookEntry.name,
                addressBookEntry.publicKey,
                addressBookEntry.address,
                addressBookEntry.imageUri ?: URI("")
            )
        }

        fun decode(value: ByteArray): InviteToken {
            try {
                return ByteArrayInputStream(value).use {
                    val token = it.readByteArray()
                    val signature = it.readByteArray()
                    val inviteToken = toInviteToken(token)

                    if (!isValidSignature(inviteToken.publicKey, token, signature))
                        throw IllegalArgumentException("Invalid signature found in InviteToken.")

                    inviteToken
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Invite Token. Request a new one.", e)
            }
        }

        fun decodeBase58(token: String): InviteToken {
            return decode(Base58.decode(token))
        }
    }
}