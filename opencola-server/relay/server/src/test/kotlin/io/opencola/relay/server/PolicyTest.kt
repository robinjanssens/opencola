package io.opencola.relay.server

import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.relay.ClientType
import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.policy.*
import io.opencola.relay.getClient
import io.opencola.security.generateKeyPair
import io.opencola.storage.newSQLiteDB
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class PolicyTest {
    private fun testCanConnectFalse(rootId: Id, policyStore: PolicyStore) {
        var client0: AbstractClient? = null

        runBlocking {
            try {
                println("Test reject connection")
                client0 = getClient(ClientType.V2, "testCanConnectFalse")
                val client0Id = Id.ofPublicKey(client0!!.publicKey)
                val policy = Policy("rejectConnect", connectionPolicy = ConnectionPolicy(false))
                policyStore.setPolicy(rootId, policy)
                policyStore.setUserPolicy(rootId, client0Id, policy.name)

                StdoutMonitor().use {
                    it.runCoroutine { client0!!.open { _, _ -> } }
                    it.waitUntil("Authentication failed: NOT_AUTHORIZED", 3000)
                }
            } finally {
                client0?.close()
            }
        }
    }

     private fun testMaxPayloadSize(rootId: Id, policyStore: PolicyStore) {
        var client: AbstractClient? = null

        runBlocking {
            try {
                println("Test max payload size")
                val maxPayloadSize = 1024L
                val clientKeyPair = generateKeyPair()
                val clientId = Id.ofPublicKey(clientKeyPair.public)
                val policy = Policy("maxMessageSize", messagePolicy = MessagePolicy(maxPayloadSize))
                policyStore.setPolicy(rootId, policy)
                policyStore.setUserPolicy(rootId, clientId, policy.name)
                assertEquals(maxPayloadSize, policyStore.getUserPolicy(rootId, clientId)!!.messagePolicy.maxPayloadSize)

                client = getClient(ClientType.V2, "maxMessageSizeClient", clientKeyPair).also {
                    launch { it.open { _, _ -> } }
                    it.waitUntilOpen()
                }

                val receiverPublicKey = generateKeyPair().public
                val receiverId = Id.ofPublicKey(receiverPublicKey)

                println("Send small message that should be accepted")
                StdoutMonitor().use {
                    client!!.sendMessage(receiverPublicKey, MessageStorageKey.none, "1".toByteArray())
                    it.waitUntil("$receiverId: no connection to receiver", 3000)
                }

                println("Send larger message that should be rejected")
                val bigMessage = "0".repeat(maxPayloadSize.toInt()).toByteArray()
                StdoutMonitor().use {
                    client!!.sendMessage(receiverPublicKey, MessageStorageKey.none, bigMessage)
                    it.waitUntil("Payload too large from $clientId", 3000)
                }
            } finally {
                client?.close()
            }
        }
    }

    private fun testMaxStoredBytes(rootId: Id, policyStore: PolicyStore) {
        var client: AbstractClient? = null

        runBlocking {
            try {
                println("Test max stored bytes")
                val maxStoredBytes = 1024L
                val clientKeyPair = generateKeyPair()

                client = getClient(ClientType.V2, "maxMessageSizeClient", clientKeyPair).also {
                    launch { it.open { _, _ -> } }
                    it.waitUntilOpen()
                }

                val receiverPublicKey = generateKeyPair().public
                val receiverId = Id.ofPublicKey(receiverPublicKey)
                val policy = Policy("maxStoredBytes", storagePolicy = StoragePolicy(maxStoredBytes))
                policyStore.setPolicy(rootId, policy)
                policyStore.setUserPolicy(rootId, receiverId, "maxStoredBytes")
                assertEquals(maxStoredBytes, policyStore.getUserPolicy(rootId, receiverId)!!.storagePolicy.maxStoredBytes)

                println("Send small message that should queued")
                StdoutMonitor().use {
                    client!!.sendMessage(receiverPublicKey, MessageStorageKey.unique(), "1".toByteArray())
                    it.waitUntil("$receiverId: no connection to receiver", 3000)
                }

                println("Send larger message that should not be stored")
                val bigMessage = "0".repeat(maxStoredBytes.toInt()).toByteArray()
                StdoutMonitor().use {
                    client!!.sendMessage(receiverPublicKey, MessageStorageKey.unique(), bigMessage)
                    it.waitUntil("Message store for $receiverId is full", 3000)
                }
            } finally {
                client?.close()
            }
        }
    }

    @Test
    fun testPolicies() {
        var server: RelayServer? = null

        runBlocking {
            try {
                val rootKeyPair = generateKeyPair()
                val rootId = Id.ofPublicKey(rootKeyPair.public)
                val policyDB = newSQLiteDB("testPolicies")
                val policyStore = ExposedPolicyStore(policyDB, rootId)
                server = RelayServer(policyStore = policyStore).also { it.start() }

                testCanConnectFalse(rootId, policyStore)
                testMaxPayloadSize(rootId, policyStore)
                testMaxStoredBytes(rootId, policyStore)
            } finally {
                server?.stop()
            }
        }
    }
}