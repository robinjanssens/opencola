package io.opencola.relay.server

import io.opencola.model.Id
import io.opencola.relay.common.connection.Connection
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.State.*
import io.opencola.relay.common.message.*
import io.opencola.relay.common.message.v2.ControlMessage
import io.opencola.relay.common.message.v2.ControlMessageType
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractRelayServer(
    protected val numChallengeBytes: Int = 32,
    protected val numSymmetricKeyBytes: Int = 32,
    private val messageStore: MessageStore? = null
) {
    private val connections = ConcurrentHashMap<Id, Connection>()
    protected val serverKeyPair = generateKeyPair()
    protected val logger = KotlinLogging.logger("RelayServer")
    protected val openMutex = Mutex(true)
    protected val random = SecureRandom()
    protected var state = Initialized
    protected var listenJob: Job? = null

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    suspend fun connectionStates(): List<Pair<String, Boolean>> {
        return connections.map { Pair(it.key.toString(), it.value.isReady()) }
    }

    fun getUsage(): Sequence<Usage> {
        return (messageStore as? MemoryMessageStore)?.getUsage() ?: emptySequence()
    }

    protected abstract suspend fun authenticate(socketSession: SocketSession): PublicKey?

    // Decode the envelope into common server format
    protected abstract fun decodePayload(from: PublicKey, payload: ByteArray): Envelope

    // Turn the Envelope into a message that the client will understand
    protected abstract fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray

    protected fun isAuthorized(clientPublicKey: PublicKey): Boolean {
        // TODO: Support client lists
        logger.debug { "Authorizing client: ${Id.ofPublicKey(clientPublicKey)}" }
        return true
    }

    private val noPendingMessagesMessage = Message(
        serverKeyPair.public,
        ControlMessage(ControlMessageType.NO_PENDING_MESSAGES).encodeProto()
    )

    private fun getQueueEmptyEnvelope(to: PublicKey): Envelope {
        return Envelope.from(serverKeyPair.private, to, null, noPendingMessagesMessage)
    }

    private suspend fun sendStoredMessages(id: Id) {
        val connection = connections[id] ?: return

        while (messageStore != null) {
            val storedMessage = messageStore.getMessages(id).firstOrNull() ?: break
            val recipient = Recipient(connection.publicKey, storedMessage.messageSecretKey)
            val envelope = Envelope(recipient, storedMessage.messageStorageKey, storedMessage.message)
            connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
            messageStore.removeMessage(storedMessage)
        }

        if (messageStore != null) {
            logger.info { "Queue empty for: $id" }
            connection.writeSizedByteArray(
                encodePayload(connection.publicKey, getQueueEmptyEnvelope(connection.publicKey))
            )
        }
    }

    suspend fun handleSession(socketSession: SocketSession) {
        authenticate(socketSession)?.let { publicKey ->
            val connection = Connection(publicKey, socketSession)
            val id = connection.id
            logger.info { "Session authenticated for: ${id}" }
            connections[id] = connection

            try {
                sendStoredMessages(id)

                // TODO: Add garbage collection on inactive connections?
                connection.listen { payload -> handleMessage(publicKey, payload) }
            } finally {
                connection.close()
                connections.remove(id)
                logger.info { "Session closed for: ${id}" }
            }
        }
    }

    abstract suspend fun open()

    private suspend fun deliverMessage(
        from: Id,
        to: Id,
        envelope: Envelope,
    ) {
        require(from != to) { "Attempt to deliver message to self" }
        val prefix = "from=$from, to=$to:"
        var messageDelivered = false

        try {
            val connection = connections[to]

            if (connection == null) {
                logger.info { "$prefix no connection to receiver" }
            } else if (!connection.isReady()) {
                logger.info { "$prefix Removing closed connection for receiver" }
            } else {
                logger.info { "$prefix Delivering ${envelope.message.bytes.size} bytes" }
                connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
                messageDelivered = true
            }
        } catch (e: Exception) {
            logger.error { "$prefix Error while handling message: $e" }
        } finally {
            try {
                if (!messageDelivered && envelope.messageStorageKey != null) {
                    val recipient = envelope.recipients.single { Id.ofPublicKey(it.publicKey) == to }
                    messageStore?.addMessage(
                        from,
                        to,
                        envelope.messageStorageKey!!,
                        recipient.messageSecretKey,
                        envelope.message
                    )
                }
            } catch (e: Exception) {
                logger.error { "Error while storing message - from: $from to: $to e: $e" }
            }
        }
    }

    private suspend fun handleMessage(from: PublicKey, payload: ByteArray) {
        val fromId = Id.ofPublicKey(from)
        try {
            decodePayload(from, payload).let { envelope ->
                logger.info { "Handling message from: $fromId to ${envelope.recipients.size} recipients" }
                envelope.recipients.forEach { recipient ->
                    try {
                        deliverMessage(fromId, Id.ofPublicKey(recipient.publicKey), envelope)
                    } catch (e: Exception) {
                        logger.error { "Error while delivering message from: $fromId to: ${recipient.id()} - $e" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error { "Error while handling message from $fromId: $e" }
        }
    }

    open suspend fun close() {
        state = Closed
        connections.values.forEach { it.close() }
        connections.clear()
        listenJob?.cancel()
        listenJob = null
    }

    companion object {
        init {
            initProvider()
        }
    }
}