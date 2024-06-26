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

package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val maxBytesStored: Long, private val policyStore: PolicyStore) : MessageStore {
    private val logger = KotlinLogging.logger("MemoryMessageStore")
    private val messageQueues = ConcurrentHashMap<Id, MessageQueue>()

    private fun getBytesStored(): Long {
        return messageQueues.values.sumOf { it.bytesStored }
    }

    override fun addMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        message: SignedBytes
    ) {
        require(storageKey != MessageStorageKey.none)
        require(storageKey.value != null)

        // TODO: This fetches the policy on every message. Would it be better to cache it for the life of the connection?
        val storagePolicy = policyStore.getUserPolicy(to, to)?.storagePolicy

        if (storagePolicy == null) {
            logger.warn { "No storage policy for $to - dropping message" }
            return
        }

        val bytesAvailable = maxBytesStored - getBytesStored()

        messageQueues
            .getOrPut(to) { MessageQueue(to, storagePolicy.maxStoredBytes) }
            .apply { addMessage(bytesAvailable, StoredMessage(from, to, storageKey, secretKey, message)) }
    }

    override fun getMessages(to: Id?): Sequence<StoredMessage> {
        val messages =
            if (to == null)
                messageQueues.flatMap { it.value.getMessages() }
            else
                messageQueues[to]?.getMessages() ?: emptyList()

        return messages.asSequence()
    }

    override fun removeMessage(header: StoredMessageHeader) {
        messageQueues[header.to]?.removeMessage(header)
    }

    override fun removeMessages(maxAgeMilliseconds: Long, limit: Int): List<StoredMessageHeader> {
        return messageQueues.values.flatMap { it.removeMessages(maxAgeMilliseconds, limit) }
    }

    override fun getUsage(): Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.numMessages, it.value.bytesStored) }
    }
}