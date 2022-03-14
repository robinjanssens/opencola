package opencola.core.storage

import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction

interface EntityStore {
    fun getEntity(authorityId: Id, entityId: Id): Entity?
    fun commitChanges(vararg entities: Entity) : SignedTransaction?
    fun addTransactions(signedTransactions: List<SignedTransaction>)
    fun getTransaction(authorityId: Id, transactionId: Long) : SignedTransaction?
    fun getTransactions(authorityId: Id, startTransactionId: Long, endTransactionId: Long = Long.MAX_VALUE) : Iterable<SignedTransaction>
    fun getTransactionId(authorityId: Id): Long

    // SHOULD ONLY BE USED FOR TESTING OR IF YOU REALLY MEAN IT
    fun resetStore() : EntityStore
}