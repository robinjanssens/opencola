package opencola.core.storage

import opencola.core.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

// TODO: Investigate: This is odd. To get a database, you must connect, but it seems that then there can only be one
//  active database and it's implicit (i.e. storing database here doesn't do anything)

class ExposedEntityStore(authority: Authority, private val database: Database) : EntityStore(authority) {
    private class Facts(authorityId: Id) : Table("fct-${authorityId.toString()}"){
        val authorityId = binary("authorityId", 32)
        val entityId = binary("entityId", 32)
        val attribute = text("attribute")
        val value = binary("value")
        val operation = enumeration("operation", Operation::class)
        val epoch = long("epoch")
    }

    private class Transactions(authorityId: Id) : Table("txs-${authorityId.toString()}") {
        val authorityId = binary("authorityId", 32)
        val signature = binary("signature") // TODO: Add signature length?
        val epoch = long("epoch")
    }

    private val facts: Facts
    private val transactions: Transactions

    init {
        logger.info { "Initializing ExposedEntityStore {${database.url}}" }

        facts = Facts(authority.authorityId)
        transactions = Transactions(authority.authorityId)

        transaction {
            SchemaUtils.create(facts)
            SchemaUtils.create(transactions)

            setEpoch(
                transactions.selectAll()
                    .orderBy(transactions.epoch to SortOrder.DESC)
                    .limit(1).firstOrNull()
                    ?.getOrNull(transactions.epoch)
                    ?: 0
            )
        }
    }


    override fun resetStore(): EntityStore {
        transaction{
            SchemaUtils.drop(facts, transactions)
        }

        return ExposedEntityStore(authority, database)
    }

    override fun getEntity(authority: Authority, entityId: Id): Entity? {
        return transaction{
            val facts = facts.select{
                (facts.authorityId eq Id.encode(authority.authorityId) and (facts.entityId eq Id.encode(entityId)))
            }.map {
                Fact(Id.decode(it[facts.authorityId]),
                    Id.decode(it[facts.entityId]),
                    CoreAttribute.values().single { a -> a.spec.uri.toString() == it[facts.attribute]}.spec,
                    Value(it[facts.value]),
                    it[facts.operation],
                    it[facts.epoch])
            }

            if(facts.isNotEmpty()) Entity.getInstance(facts) else null
        }
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        // val facts = signedTransaction.expandFacts()

        transaction{
            signedTransaction.expandFacts().forEach{ fact ->
                facts.insert {
                    it[authorityId] = Id.encode(fact.authorityId)
                    it[entityId] = Id.encode(fact.entityId)
                    it[attribute] = fact.attribute.uri.toString()
                    it[value] = fact.value.bytes
                    it[operation] = fact.operation
                    it[epoch] = fact.transactionId
                }
            }

            transactions.insert {
                it[authorityId] = Id.encode(signedTransaction.transaction.authorityId)
                it[signature] = signedTransaction.signature
                it[epoch] = signedTransaction.transaction.id
            }
        }
    }
}