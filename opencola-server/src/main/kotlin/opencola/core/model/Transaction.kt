package opencola.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.security.Signator
import opencola.core.security.isValidSignature
import opencola.core.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

@Serializable
// TODO: Change id to epoch
// TODO: Add timestamp
// TODO: Should id be a long? Would int suffice?
// TODO: Change List<T>s to Iterable<T>s
data class Transaction(val authorityId: Id, val epoch: Long, val transactionFacts: List<TransactionFact>){
    fun getFacts(): List<Fact> {
        return transactionFacts.map { Fact(authorityId, it.entityId, it.attribute, it.value, it.operation, epoch) }
    }

    // TODO: This is common to a number of classes. Figure out how to make properly generic
    override fun toString(): String {
        return Json.encodeToString(this)
    }

    fun sign(signator: Signator) : SignedTransaction {
        // This is probably not the right way to serialize. Likely should create a serializer / provider that can be
        // configured to serialize in an appropriate format.
        // TODO: Validate transaction
        // TODO: Switch to efficient serialization
        return SignedTransaction(this, signator.signBytes(authorityId, this.toString().toByteArray()))
    }

    @Serializable
    // TODO: Consider making value a Value with a clean string serializer (signature too). Maybe doesn't matter with protobuf, but nice for json
    data class TransactionFact(val entityId: Id, val attribute: Attribute, val value: Value, val operation: Operation) {
         companion object Factory : StreamSerializer<TransactionFact> {
            fun fromFact(fact: Fact): TransactionFact {
                return TransactionFact(fact.entityId, fact.attribute, fact.value, fact.operation)
            }

             override fun encode(stream: OutputStream, value: TransactionFact) {
                 Id.encode(stream, value.entityId)
                 Attribute.encode(stream, value.attribute)
                 Value.encode(stream, value.value)
                 Operation.encode(stream, value.operation)
             }

             override fun decode(stream: InputStream): TransactionFact {
                 return TransactionFact(Id.decode(stream), Attribute.decode(stream), Value.decode(stream), Operation.decode(stream))
             }
         }
    }

    companion object Factory : StreamSerializer<Transaction> {
        fun fromFacts(id: Long, facts: List<Fact>) : Transaction {
            return Transaction(facts.first().authorityId, id, validateFacts(facts).map{ TransactionFact.fromFact(it) })
        }

        private fun validateFacts(facts: List<Fact>) : List<Fact> {
            val authorities = facts.map { it.authorityId }.distinctBy { it.toString() }

            if (authorities.size != 1) {
                throw IllegalArgumentException("Attempt to construct transaction with facts from multiple authorities: ${authorities.joinToString()}")
            }

            return facts
        }

        override fun encode(stream: OutputStream, value: Transaction) {
            Id.encode(stream, value.authorityId)
            writeLong(stream, value.epoch)
            writeInt(stream, value.transactionFacts.size)
            for(fact in value.transactionFacts){
                TransactionFact.encode(stream, fact)
            }
        }

        override fun decode(stream: InputStream): Transaction {
            return Transaction(Id.decode(stream), readLong(stream), readInt(stream).downTo(1).map { TransactionFact.decode(stream) })
        }
    }
}

// TODO: data class?
// TODO: Include signature alg
@Serializable
data class SignedTransaction(val transaction: Transaction, val signature: ByteArray) {
    // TODO: Fix signature serialization - right now json array vs. an encoded hex string
    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, transaction.toString().toByteArray(), signature)
    }

    fun expandFacts() : Iterable<Fact> {
        return transaction.transactionFacts.map {
            Fact(transaction.authorityId, it.entityId, it.attribute, it.value, it.operation, transaction.epoch)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (transaction != other.transaction) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object Factory : StreamSerializer<SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            Transaction.encode(stream, value.transaction)
            writeByteArray(stream, value.signature)
        }

        override fun decode(stream: InputStream): SignedTransaction {
            return SignedTransaction(Transaction.decode(stream), readByteArray(stream))
        }

    }
}

