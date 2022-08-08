package opencola.core.network.providers.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import opencola.core.config.ServerConfig
import opencola.core.model.Authority
import opencola.core.network.*
import opencola.core.storage.AddressBook
import java.lang.IllegalStateException
import java.net.URI

class HttpNetworkProvider(serverConfig: ServerConfig, addressBook: AddressBook) : AbstractNetworkProvider(addressBook) {
    private val logger = KotlinLogging.logger("HttpNetworkProvider")
    val serverAddress = URI("http://${serverConfig.host}:${serverConfig.port}")

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    override fun getAddress(): URI {
        return serverAddress
    }

    override fun updatePeer(peer: Authority) {
        // Nothing to do
    }

    override fun removePeer(peer: Authority) {
        // Nothing to do
    }

    // Caller (Network Node) should check if peer is active
    override fun sendRequest(peer: Authority, request: Request) : Response? {
        try {
//            if(!addressBook.isAuthorityActive(peer)) {
//                logger.warn { "Ignoring message to inactive peer: ${peer.entityId}" }
//                return
//            }

            val urlString = "${peer.uri}/networkNode"
            logger.info { "Sending request $request" }

            return runBlocking {
                val httpResponse = httpClient.post<HttpStatement>(urlString) {
                    // TODO: Support more efficient, binary formats
                    contentType(ContentType.Application.Json)
                    body = request
                }.execute()

                val response = Json.decodeFromString<Response>(String(httpResponse.readBytes()))
                logger.info { "Response: $response" }
                response
            }

            // peerStatuses[peer.entityId] = NetworkNode.PeerStatus.Online
        }
        catch(e: java.net.ConnectException){
            logger.info { "${peer.name} appears to be offline." }
            // peerStatuses[peer.entityId] = NetworkNode.PeerStatus.Offline
        }
        catch (e: Exception){
            logger.error { e.message }
            // peerStatuses[peer.entityId] = NetworkNode.PeerStatus.Offline
        }

        return null
    }

    fun handleRequest(request: Request) : Response {
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        return handler(request)
    }
}