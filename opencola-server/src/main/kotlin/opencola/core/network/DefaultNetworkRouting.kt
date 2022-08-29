package opencola.core.network

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore

private val logger = KotlinLogging.logger("RequestRouting")

// TODO - This should change to handlePeerEvent
fun handleNotification(addressBook: AddressBook, eventBus: EventBus, notification: Notification) {
    logger.info { "Received notification: $notification" }

    addressBook.getAuthority(notification.peerId)
        ?: throw IllegalArgumentException("Received notification from unknown peer: ${notification.peerId}")

    eventBus.sendMessage(Events.PeerNotification.toString(), notification.encode())
}

@Serializable
data class TransactionsResponse(
    val startTransactionId: Id?,
    val currentTransactionId: Id?,
    val transactions: List<SignedTransaction>
)

//TODO: This should return transactions until the root transaction, not all transactions for the authority in the
// store, as the user a peer may have deleted their store, which creates a new HEAD. Only the transaction for the
// current chain should be propagated to other peers
fun handleGetTransactions(
    entityStore: EntityStore,
    addressBook: AddressBook,
    authorityId: Id, // Id of user transactions are being requested for
    peerId: Id, // Id of user making request
    transactionId: Id?,
    numTransactions: Int?,
): TransactionsResponse {
    logger.info { "handleGetTransactionsCall authorityId: $authorityId, peerId: $peerId, transactionId: $transactionId" }

    if(addressBook.getAuthority(peerId) == null){
        throw RuntimeException("Unknown peer attempted to request transactions: $peerId")
    }

    val extra = (if (transactionId == null) 0 else 1)
    val totalNumTransactions = (numTransactions ?: 10) + extra
    val currentTransactionId = entityStore.getLastTransactionId(authorityId)
    val transactions = entityStore.getSignedTransactions(
        authorityId,
        transactionId,
        EntityStore.TransactionOrder.IdAscending,
        totalNumTransactions
    ).drop(extra)

    return TransactionsResponse(transactionId, currentTransactionId, transactions.toList())
}

fun getDefaultRoutes(
    eventBus: EventBus,
    entityStore: EntityStore,
    addressBook: AddressBook,
): List<Route> {

    return listOf(
        Route(
            Request.Method.GET,
            "/ping"
        ) { Response(200, "Pong") },
        Route(
            Request.Method.POST,
            "/notifications"
        ) { request ->
            val notification = request.decodeBody<Notification>()
                ?: throw IllegalArgumentException("Body must contain Notification")

            handleNotification(addressBook, eventBus, notification)
            Response(200)
        },

        Route(
            Request.Method.GET,
            "/transactions"
        ) { request ->
            if (request.parameters == null) {
                throw IllegalArgumentException("/transactions call requires parameters")
            }

            val authorityId =
                Id.decode(request.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
            val peerId = request.from
            val transactionId = request.parameters["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
            val numTransactions = request.parameters["numTransactions"].nullOrElse { it.toInt() }


            val transactionResponse =
                handleGetTransactions(
                    entityStore,
                    addressBook,
                    authorityId,
                    peerId,
                    transactionId,
                    numTransactions
                )

            response(200, "OK", null, transactionResponse)
        }
    )
}