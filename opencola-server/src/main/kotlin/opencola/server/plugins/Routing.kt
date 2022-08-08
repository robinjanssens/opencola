package opencola.server.plugins

import io.ktor.application.*
import io.ktor.application.Application
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.NetworkNode.*
import opencola.core.network.Request
import opencola.core.network.providers.http.HttpNetworkProvider
import opencola.server.handlers.*
import opencola.core.config.Application as app

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureRouting(app: app) {
    // TODO: Make and user general opencola.server
    val logger = KotlinLogging.logger("opencola.init")

    routing {
        get("/search"){
            handleGetSearchCall(call, app.inject())
        }

        get("/entity/{entityId}"){
            // TODO: Authority should be passed (and authenticated) in header
            getEntity(call, app.inject(), app.inject(), app.inject())
        }

        post("/entity/{entityId}"){
            saveEntity(call, app.inject(), app.inject(), app.inject())
        }

        put("/entity/{entityId}"){
            updateEntity(call, app.inject(), app.inject(), app.inject())
        }

        delete("/entity/{entityId}") {
            deleteEntity(call, app.inject(), app.inject(), app.inject())
        }

        post("/entity/{entityId}/comment"){
            addComment(call, app.inject(), app.inject(), app.inject())
        }

        post("/post"){
            newPost(call, app.inject(), app.inject(), app.inject())
        }

        delete("/comment/{commentId}"){
            // TODO: Remove call and parse comment id out here, so handlers don't need to know anything about ktor
            deleteComment(call, app.inject(), app.inject())
        }

        // TODO: Think about checking for no extra parameters
        suspend fun getTransactions(call: ApplicationCall) {
            val authorityId =
                Id.decode(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
            val peerId = Id.decode(call.parameters["peerId"] ?: throw IllegalArgumentException("No peerId set"))
            val transactionId = call.parameters["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
            val numTransactions = call.parameters["numTransactions"].nullOrElse { it.toInt() }

            call.respond(handleGetTransactionsCall(app.inject(), app.inject(), authorityId, peerId, transactionId, numTransactions))
        }

        get("/transactions/{authorityId}") {
            getTransactions(call)
        }

        get("/transactions/{authorityId}/{mostRecentTransactionId}") {
            getTransactions(call)
        }

        get("/data/{id}"){
            val authority = app.inject<Authority>()
            handleGetDataCall(call, app.inject(), authority.authorityId)
        }

        get("/data/{id}/{partName}"){
            // TODO: Add a parameters extension that gets the parameter value or throws an exception
            val authority = app.inject<Authority>()
            handleGetDataPartCall(call, authority.authorityId, app.inject())
        }

        post("/action"){
            val authority = app.inject<Authority>()
            handlePostActionCall(call, authority.authorityId, app.inject(), app.inject(), app.inject())
        }

        get("/actions/{uri}"){
            val authority = app.inject<Authority>()
            handleGetActionsCall(call, authority.authorityId, app.inject())
        }

        post("/notifications") {
            val notification = call.receive<Notification>()
            handlePostNotification(app.inject(), app.inject(), notification)
            call.respond(HttpStatusCode.OK)
        }

        get("/feed"){
            // TODO: Handle filtering of authorities
            handleGetFeed(call, app.inject(), app.inject(), app.inject(), app.inject())
        }

        get("/peers") {
            call.respond(getPeers(app.inject(), app.inject()))
        }

        get("/peers/token") {
            call.respond(getToken(app.inject(), app.inject()))
        }

        post("/peers/token") {
            val tokenRequest = call.receive<TokenRequest>()
            call.respond(inviteTokenToPeer(app.inject(), tokenRequest.token))
        }

        put("/peers") {
            val peer = call.receive<Peer>()
            updatePeer(app.inject(), peer)
            call.respond("{}")
        }

        delete("/peers/{peerId}") {
            val peerId = Id.decode(call.parameters["peerId"] ?: throw IllegalArgumentException("No id set"))
            deletePeer(app.inject(), peerId)
            call.respond("{}")
        }

        post("/networkNode") {
            val httpNetworkProvider = app.inject<HttpNetworkProvider>()
            val request = call.receive<Request>()
            val response = httpNetworkProvider.handleRequest(request)
            call.respond(response)
        }

        static(""){
            val resourcePath = app.applicationPath.resolve("resources")
            logger.info("Initializing static resources from $resourcePath")
            file("/", resourcePath.resolve("index.html").toString())
            files(resourcePath.toString())
        }
    }
}
