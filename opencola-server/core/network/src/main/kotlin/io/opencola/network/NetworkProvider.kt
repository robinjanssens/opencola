package io.opencola.network

import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.model.Persona
import io.opencola.security.*
import io.opencola.storage.AddressBook
import io.opencola.security.Encryptor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

interface NetworkProvider {
    fun start(waitUntilReady: Boolean = false)
    fun stop()

    fun getScheme() : String
    fun validateAddress(address: URI)

    // If a peer URI changes with the same provider, it will result in removePeer(oldPeer) addPeer(newPeer)
    fun addPeer(peer: Authority)
    fun removePeer(peer: Authority)

    fun sendRequest(from: Authority, to: Authority, request: Request): Response?
    fun setRequestHandler(handler: (Id, Id, Request) -> Response)
}

abstract class AbstractNetworkProvider(val addressBook: AddressBook,
                                       val signator: Signator,
                                       val encryptor: Encryptor,
) : NetworkProvider {
    var handler: ((Id, Id, Request) -> Response)? = null
    var started = false

    override fun setRequestHandler(handler: (Id, Id, Request) -> Response) {
        this.handler = handler
    }

    fun getEncodedEnvelope(fromId: Id, toId: Id, messageBytes: ByteArray, encryptMessage: Boolean): ByteArray {
        val toAuthority = addressBook.getAuthority(fromId, toId)
            ?: throw IllegalArgumentException("Attempt to construct message to unknown peer: $toId")

        val toPublicKey = toAuthority.publicKey
            ?: throw IllegalArgumentException("Can't construct message to peer that does not have a public key: $toId")

        val message = Message(fromId, messageBytes, signator.signBytes(fromId.toString(), messageBytes))
        return MessageEnvelope(toId, message).encode(if (encryptMessage) toPublicKey else null)
    }

    fun validateMessageEnvelope(messageEnvelope: MessageEnvelope) {
        if(addressBook.getAuthority(messageEnvelope.to, messageEnvelope.to) !is Persona) {
            throw IllegalArgumentException("Received message for non local authority: ${messageEnvelope.to}")
        }

        val message = messageEnvelope.message

        val fromAuthority = addressBook.getAuthority(messageEnvelope.to, message.from)
            ?: throw IllegalArgumentException("Message is from unknown authority: ${message.from}")

        val fromPublicKey = fromAuthority.publicKey
            ?: throw IllegalArgumentException("Received message from authority that does not have a public key: ${message.from}")

        if(!isValidSignature(fromPublicKey, message.body, message.signature)) {
            throw IllegalArgumentException("Received message from $fromAuthority with invalid signature")
        }
    }

    fun handleMessage(envelopeBytes: ByteArray, useEncryption: Boolean) : ByteArray {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        val encryptor = if (useEncryption) this.encryptor else null
        val envelope = MessageEnvelope.decode(envelopeBytes, encryptor).also { validateMessageEnvelope(it) }
        val response =  handler(envelope.message.from, envelope.to, Json.decodeFromString(String(envelope.message.body)))

        return getEncodedEnvelope(envelope.to, envelope.message.from, Json.encodeToString(response).toByteArray(), useEncryption)
    }
}