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

package io.opencola.storage.entitystore

import mu.KotlinLogging
import io.opencola.event.bus.EventBus
import io.opencola.event.bus.Events
import io.opencola.model.*
import io.opencola.model.value.EmptyValue
import io.opencola.model.value.Value
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import io.opencola.util.CompressionFormat
import io.opencola.util.compress

// TODO: Should support multiple authorities
// TODO: Organize methods by CRUD
abstract class AbstractEntityStore(
    val signator: Signator,
    val publicKeyProvider: PublicKeyProvider<Id>,
    val eventBus: EventBus?,
    val transactionEncodingFormat: EncodingFormat,
) : EntityStore {
    // TODO: Assumes transaction has been validated. Cleanup?
    protected abstract fun persistTransaction(signedTransaction: SignedTransaction): Long

    private fun getFirstTransactionId(authorityId: Id): Id {
        // TODO: Random, or rooted with authority?
        return Id.ofData("$authorityId.firstTransaction".toByteArray())
    }

    // TODO: Make logger class?
    protected val logger = KotlinLogging.logger("EntityStore")
    protected fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }

    // TODO - make entity method?
    private fun validateEntity(entity: Entity): Entity {
        val allFacts = entity.getAllFacts()
        val authorityIds = allFacts.map { it.authorityId }.distinct()

        if (authorityIds.size != 1) {
            logAndThrow(RuntimeException("Entity{${entity.entityId}} contains facts from multiple authorities $authorityIds }"))
        }

        val authorityId = authorityIds.single()
        if (entity.authorityId != authorityId) {
            logAndThrow(RuntimeException("Entity{${entity.entityId}} with authority ${entity.authorityId} contains facts from wrong authority $authorityId }"))
        }

        val invalidEntityIds = allFacts.filter { it.entityId != entity.entityId }.map { it.entityId }
        if (invalidEntityIds.isNotEmpty()) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains facts not matching its id: $invalidEntityIds"))
        }

        if (allFacts.distinct().size < allFacts.size) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains non-distinct facts"))
        }

        if (allFacts.any { it.operation == Operation.Add && it.value == EmptyValue }) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains Add operation with empty value"))
        }

        // TODO: Check that all transaction ids exist (0 to current) and don't surpass the current transaction id
        // TODO: Check that subsequent facts (by transactionId) for the same property are not equal
        // TODO: Check for duplicate facts (and add unit tests)
        return entity
    }

    override fun updateEntities(vararg entities: Entity): SignedTransaction? {
        if (entities.isEmpty()) {
            return null
        }

        entities.forEach { validateEntity(it) }

        if (entities.distinctBy { it.entityId }.size != entities.size) {
            logAndThrow(RuntimeException("Attempt to commit changes to multiple entities with the same id."))
        }

        val authorityIds = entities.map { it.authorityId }.toSet()

        if (authorityIds.size != 1) {
            logAndThrow(RuntimeException("Attempt to commit changes to multiple authorities."))
        }

        val authorityId = authorityIds.first()

        if (!signator.canSign(authorityId.toString())) {
            logAndThrow(RuntimeException("Attempt to commit changes for authority without private key."))
        }

        val uncommittedFacts = entities.flatMap { it.getAllFacts() }.filter { it.transactionOrdinal == null }
        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update with no novel facts" }
            return null
        }

        val persistTransactionResult = persistTransaction(authorityId, uncommittedFacts)
        entities.forEach {
            it.commitFacts(persistTransactionResult.epochSecond, persistTransactionResult.transactionOrdinal)
        }

        return persistTransactionResult.signedTransaction
    }

    override fun getEntities(authorityIds: Set<Id>, entityIds: Set<Id>): List<Entity> {
        return getFacts(authorityIds, entityIds)
            .groupBy { Pair(it.authorityId, it.entityId) }
            .mapNotNull { Entity.fromFacts(it.value) }
    }

    private fun computedFacts(facts: Iterable<Fact>): List<Fact> {
        return CoreAttribute.entries.flatMap { attribute ->
            attribute.spec.computeFacts?.let { it(facts) } ?: emptyList()
        }
    }

    private fun validFact(facts: List<Fact>, fact: Fact): Boolean {
        return when (fact.operation) {
            // Don't allow superfluous adds
            Operation.Add ->
                !facts.any {
                    it.authorityId == fact.authorityId
                            && it.entityId == fact.entityId
                            && it.attribute == fact.attribute
                            && it.operation == fact.operation
                            && it.value == fact.value
                }

            Operation.Retract ->
                // Don't allow superfluous retractions
                facts.any {
                    it.authorityId == fact.authorityId
                            && it.entityId == fact.entityId
                            && it.attribute == fact.attribute
                            && it.operation == Operation.Add
                            && (it.attribute.type == AttributeType.SingleValue || it.value == fact.value)
                }
        }
    }

    // TODO: Merge with validateEntity
    private fun validateFacts(authorityId: Id, facts: List<Fact>): List<Fact> {
        // TODO: Since there are already "bad" facts out there, this will likely create an issue of blowing
        //  up anybody that gets bad facts. Figure out how to fix. Likely need to rebuild transaction chain then
        //  dis/reconnect to peers. Other option is to gracefully handle bad facts, but only from peers
        val transactionFactsByEntity = facts.groupBy { it.entityId }
        val existingEntities = getEntities(setOf(authorityId), transactionFactsByEntity.keys)

        existingEntities.forEach { entity ->
            val currentFacts = entity.getCurrentFacts()
            val transactionsFacts = transactionFactsByEntity[entity.entityId]!!

            if (transactionsFacts.any { !validFact(currentFacts, it) }) {
                throw IllegalArgumentException("Detected duplicate fact")
            }
        }

        facts.forEach {
            if (it.value == EmptyValue && it.operation == Operation.Add) {
                throw IllegalArgumentException("Facts should not have empty values on add operation")
            }
        }

        return facts
    }

    // TODO: !!! Clean up validation !!!

    // DO NOT use this outside of persistTransaction
    protected fun getNextTransactionId(authorityId: Id): Id {
        return getSignedTransactions(setOf(authorityId), null, TransactionOrder.IdDescending, 1)
            .firstOrNull()
            ?.let {
                val encodedSignedTransaction =
                    when (transactionEncodingFormat) {
                        EncodingFormat.OC -> it.encode()
                        EncodingFormat.PROTOBUF -> it.encodeProto()
                        else -> throw IllegalArgumentException("Unsupported encoding format: $transactionEncodingFormat")
                    }
                Id.ofData(encodedSignedTransaction)
            } ?: getFirstTransactionId(authorityId)
    }

    private fun encodeTransaction(encodingFormat: EncodingFormat, transaction: Transaction): ByteArray {
        return when (encodingFormat) {
            EncodingFormat.OC -> Transaction.encode(transaction)
            EncodingFormat.PROTOBUF -> Transaction.encodeProto(transaction)
            else -> throw IllegalArgumentException("Unsupported encoding format: $transactionEncodingFormat")
        }
    }

    private fun compressionFormat(encodingFormat: EncodingFormat): CompressionFormat {
        return when (encodingFormat) {
            EncodingFormat.OC -> CompressionFormat.NONE
            EncodingFormat.PROTOBUF -> CompressionFormat.DEFLATE
            else -> throw IllegalArgumentException("Unsupported encoding format: $transactionEncodingFormat")
        }
    }

    private fun toSignedTransaction(transaction: Transaction): SignedTransaction {
        val encodedTransaction = encodeTransaction(transactionEncodingFormat, transaction)
        val compressedTransaction = compress(compressionFormat(transactionEncodingFormat), encodedTransaction)
        val signature = signator.signBytes(transaction.authorityId.toString(), compressedTransaction.bytes)
        return SignedTransaction(transactionEncodingFormat, compressedTransaction, signature)
    }

    data class PersistTransactionResult(
        val transactionOrdinal: Long,
        val epochSecond: Long,
        val signedTransaction: SignedTransaction
    )

    // It is critical that this function is synchronized and not bypassed. It determines the next transaction
    // id, which needs to be unique, and does a final consistency / conflict check that can't be done in the DB
    @Synchronized
    private fun persistTransaction(authorityId: Id, facts: List<Fact>): PersistTransactionResult {
        // TODO: Move validate to here
        require(facts.isNotEmpty()) { "Attempt to persist transaction with no facts" }

        val allFacts = validateFacts(authorityId, facts.plus(computedFacts(facts)).distinct())
        val transaction = Transaction.fromFacts(getNextTransactionId(authorityId), allFacts)
        val signedTransaction = toSignedTransaction(transaction)
        val transactionOrdinal = persistTransaction(signedTransaction)

        // TODO: Once switched over to all protobuf, just use SignedTransaction.encode
        eventBus?.sendMessage(
            Events.NewTransaction.toString(),
            SignedTransaction.toProto(signedTransaction).toByteArray()
        )

        return PersistTransactionResult(transactionOrdinal, transaction.epochSecond, signedTransaction)

    }

    private fun getDeletedValue(fact: Fact): Value<Any> {
        return if (fact.attribute.type != AttributeType.SingleValue
            || fact.attribute == CoreAttribute.Type.spec
            // ParentId and TopLevelParentId need to be specified on delete, as these values
            // are used to compute facts for the parent entity
            || fact.attribute == CoreAttribute.ParentId.spec
            || fact.attribute == CoreAttribute.TopLevelParentId.spec
        )
            fact.value
        else
            EmptyValue
    }

    private fun getDependentEntityIds(personaId: Id, entityId: Id): Set<Id> {
        val entity = getEntity(personaId, entityId)
        val allCommentIds = entity?.commentIds?.toSet() ?: emptySet()

        return if (allCommentIds.isEmpty())
            emptySet()
        else
            getEntities(setOf(personaId), allCommentIds).map { it.entityId }.toSet()
    }

    override fun deleteEntities(authorityId: Id, vararg entityIds: Id) {
        val entityIdsWithDependents = entityIds.flatMap { getDependentEntityIds(authorityId, it) }.toSet() + entityIds

        val facts = entityIdsWithDependents.flatMap { entityId ->
            getEntity(authorityId, entityId)?.let { entity ->
                entity.getCurrentFacts()
                    .map { Fact(authorityId, it.entityId, it.attribute, getDeletedValue(it), Operation.Retract) }
            } ?: emptyList()
        }

        if (facts.isNotEmpty())
            persistTransaction(authorityId, facts)
    }

    @Synchronized
    override fun addSignedTransactions(signedTransactions: List<SignedTransaction>) {
        signedTransactions.forEach {
            val transaction = it.transaction
            val transactionAuthorityId = transaction.authorityId
            val publicKey = publicKeyProvider.getPublicKey(transactionAuthorityId)
                ?: throw IllegalArgumentException("No public key for: $transactionAuthorityId - cannot persist transaction ${transaction.id}")

            if (!it.hasValidSignature(publicKey))
                throw IllegalArgumentException("Transaction ${transaction.id} from $transactionAuthorityId has invalid signature")

            if(getTransaction(transaction.id) != null)
                logger.warn("Transaction ${transaction.id} already exists - skipping add")
            else if (transaction.id != getNextTransactionId(transactionAuthorityId))
                logger.warn("Transaction ${transaction.id} is out of order from $transactionAuthorityId - skipping add")
            else {
                logger.info { "Adding transaction ${transaction.id} from $transactionAuthorityId" }
                persistTransaction(it)
                // TODO: Once switched over to all protobuf, just use SignedTransaction.encode
                eventBus?.sendMessage(Events.NewTransaction.toString(), it.encodeProto())
            }
        }
    }
}