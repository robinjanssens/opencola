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

package io.opencola.relay.server.v2

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.opencola.event.log.EventLogger
import io.opencola.model.Id
import io.opencola.relay.common.connection.Connection
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.v2.EnvelopeHeader
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.relay.server.AbstractRelayServer
import io.opencola.relay.server.RelayConfig
import io.opencola.security.*
import io.opencola.util.Base58
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.net.ConnectException
import java.net.URI
import java.security.PublicKey

abstract class Server(
    config: RelayConfig,
    eventLogger: EventLogger,
    policyStore: PolicyStore,
    connectionDirectory: ConnectionDirectory,
    messageStore: MessageStore?,
) : AbstractRelayServer(config, eventLogger, policyStore, connectionDirectory, messageStore) {
    private val httpClient = HttpClient()

    private fun isClientAuthorized(clientId: Id): Boolean {
        val policy = policyStore.getUserPolicy(clientId, clientId)

        return if (policy == null) {
            event.info("NoPolicyForClient") { "No policy found for client: $clientId" }
            false
        } else if (!policy.connectionPolicy.canConnect) {
            event.warn("ClientNotAuthorized") { "Client not authorized to connect: $clientId" }
            false
        } else {
            true
        }
    }

    private fun onCloseConnection(connection: Connection) {
        val entry = connectionDirectory.get(connection.id)

        if (entry != null) {
            if (entry.connection == null || entry.connection === connection) {
                logger.info { "Removing connection: ${connection.id}" }
                connectionDirectory.remove(connection.id)
            }

            if (entry.connection == null) {
                event.error("ConnectionAlreadyClosed") { "Attempt to close connection that is already closed: ${connection.id}" }
            } else if (entry.connection !== connection) {
                event.error("ConnectionMismatch") { "Attempt to close connection that does not match directory: ${connection.id}" }
            }
        }
    }

    override suspend fun authenticate(socketSession: SocketSession): Connection? {
        try {
            logger.debug { "Sending server identity" }
            // TODO: Figure out how to publish this key so client can ensure server is trusted
            socketSession.writeSizedByteArray(IdentityMessage(serverKeyPair.public).encodeProto())

            logger.debug { "Reading server challenge" }
            val serverChallenge = ChallengeMessage.decodeProto(socketSession.readSizedByteArray())

            logger.debug { "Writing challenge response" }
            val signedBytes = sign(serverKeyPair.private, serverChallenge.challenge, serverChallenge.algorithm)
            socketSession.writeSizedByteArray(ChallengeResponse(signedBytes.signature).encodeProto())

            logger.debug { "Reading client identity" }
            val encryptedClientIdentity = EncryptedBytes.decodeProto(socketSession.readSizedByteArray())
            val clientIdentity = IdentityMessage.decodeProto(decrypt(serverKeyPair.private, encryptedClientIdentity))
            val clientPublicKey = clientIdentity.publicKey
            val clientId = Id.ofPublicKey(clientPublicKey)
            logger.info { "Authenticating $clientId" }

            logger.debug { "Writing client challenge" }
            val clientChallenge =
                ChallengeMessage(DEFAULT_SIGNATURE_ALGO, random.nextBytes(config.security.numChallengeBytes))
            socketSession.writeSizedByteArray(clientChallenge.encodeProto())

            logger.debug { "Reading challenge response" }
            val encryptedChallengeResponse = EncryptedBytes.decodeProto(socketSession.readSizedByteArray())
            val clientChallengeResponse =
                ChallengeResponse.decodeProto(decrypt(serverKeyPair.private, encryptedChallengeResponse))

            val status = if (
                clientChallengeResponse.signature.algorithm != DEFAULT_SIGNATURE_ALGO ||
                !isValidSignature(clientPublicKey, clientChallenge.challenge, clientChallengeResponse.signature)
            )
                AuthenticationStatus.FAILED_CHALLENGE
            else if (!isClientAuthorized(clientId)) {
                AuthenticationStatus.NOT_AUTHORIZED
            } else {
                AuthenticationStatus.AUTHENTICATED
            }

            val authenticationResult = AuthenticationResult(status).encodeProto()
            if (status == AuthenticationStatus.AUTHENTICATED) {
                logger.debug { "Client authenticated" }
                val connection = Connection(clientPublicKey, socketSession) { onCloseConnection(it) }

                // It is important to add the connection to the directory BEFORE returning the authentication result
                // to the client so that the client can't make a request that may result in a response from the
                // server, which would fail without an entry in the connection directory.
                connectionDirectory.add(connection)
                socketSession.writeSizedByteArray(authenticationResult)
                return connection
            } else {
                event.warn("AuthenticationFailed") { "Authentication failed for $clientId: $status" }
                socketSession.writeSizedByteArray(authenticationResult)
            }
        } catch (e: CancellationException) {
            // Let job cancellation fall through
        } catch (e: ClosedReceiveChannelException) {
            // Don't bother logging on closed connections
        } catch (e: Exception) {
            event.warn("AuthenticateError") { "$e" }
            socketSession.close()
        }

        return null
    }

    override fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray {
        return PayloadEnvelope.from(serverKeyPair.private, to, envelope).encodeProto()
    }

    override fun decodePayload(from: PublicKey, payload: ByteArray): Envelope {
        val envelopeV2 = PayloadEnvelope.decodeProto(payload)
        val envelopeHeader = EnvelopeHeader.decryptAndVerifySignature(serverKeyPair.private, from, envelopeV2.header)
        return Envelope(envelopeHeader.recipients, envelopeHeader.messageStorageKey, envelopeV2.message)
    }

    override suspend fun forwardMessage(
        serverAddress: URI,
        from: PublicKey,
        to: List<Id>,
        envelope: Envelope,
        payload: ByteArray
    ) {
        val fromId = Id.ofPublicKey(from)
        val storeMessages = {
            logger.info { "Storing message from: $fromId to: $to" }
            to.forEach {
                storeMessage(fromId, it, envelope)
            }
        }

        try {
            require(serverAddress != address) { "Attempt to trigger remote delivery to self" }
            val fromEncoded = Base58.encode(from.encoded)

            val status =
                httpClient.post(Url("http://${serverAddress.host}:${serverAddress.port}/v2/forward/$fromEncoded")) {
                    setBody(payload)
                }.status
            if (status != HttpStatusCode.Accepted) {
                event.error("ForwardingError") { "Error while forwarding message from: $fromId to: $to status: $status" }
                storeMessages()
            }
        } catch (e: Exception) {
            if (e is ConnectException) {
                // Failed to connect, so assume the server is down and remove recipients from directory
                to.forEach {
                    connectionDirectory.remove(it)
                    event.warn("ServerUnreachable") { "Unable to connect to server $serverAddress: Removed $to from directory" }
                    // TODO: What if this was a temporary partition?
                }
            } else {
                logger.error { "Error while forwarding message from: $fromId to: $to e: ${e.message}" }
            }

            // Failed to send, so store the message for later delivery
            storeMessages()
        }
    }

    suspend fun handleForwardedMessage(from: PublicKey, payload: ByteArray) {
        handleMessage(from, payload, deliverRemoteMessages = false)
    }
}