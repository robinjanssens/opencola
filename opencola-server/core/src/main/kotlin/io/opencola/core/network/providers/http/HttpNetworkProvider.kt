package io.opencola.core.network.providers.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import io.opencola.core.config.ServerConfig
import io.opencola.core.model.Authority
import io.opencola.core.network.AbstractNetworkProvider
import io.opencola.core.network.*
import io.opencola.core.security.Signator
import kotlinx.serialization.encodeToString
import io.opencola.core.extensions.toHexString
import java.lang.IllegalStateException
import java.net.URI

class HttpNetworkProvider(serverConfig: ServerConfig, private val authority: Authority, private val signator: Signator) : AbstractNetworkProvider() {
    private val logger = KotlinLogging.logger("HttpNetworkProvider")
    var started = false
    private val serverAddress = URI("http://${serverConfig.host}:${serverConfig.port}")

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation){
            json()
        }
    }

    override fun start(waitUntilReady: Boolean) {
        started = true
        logger.info { "Started" }
    }

    override fun stop() {
        started = false
        logger.info { "Stopped" }
    }

    override fun getScheme(): String {
        return "http"
    }

    override fun validateAddress(address: URI) {
        // Just needs to be a valid URI
        return
    }

    override fun addPeer(peer: Authority) {
        // Nothing to do
    }

    override fun removePeer(peer: Authority) {
        // Nothing to do
    }

    // Caller (Network Node) should check if peer is active
    override fun sendRequest(peer: Authority, request: Request) : Response? {
        if (!started) throw IllegalStateException("Provider is not started - can't sendRequest")

        try {
            val urlString = "${peer.uri}/networkNode"
            logger.info { "Sending request $request" }

            return runBlocking {
                val httpResponse = httpClient.post(urlString) {
                    contentType(ContentType.Application.OctetStream)
                    val payload = Json.encodeToString(request).toByteArray()
                    val signature = signator.signBytes(request.from, payload)
                    headers.append("oc-signature", signature.toHexString())
                    setBody(payload)
                }

                if(httpResponse.status != HttpStatusCode.OK)
                    throw RuntimeException("Peer request resulted in error ${httpResponse.status}")

                val response = Json.decodeFromString<Response>(String(httpResponse.body<ByteArray>()))
                logger.info { "Response: $response" }
                response
            }
        }
        catch(e: java.net.ConnectException){
            logger.info { "${peer.name} appears to be offline." }
        }
        catch (e: Exception){
            logger.error { e.message }
        }

        return null
    }

    fun handleRequest(request: Request) : Response {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        return handler(request)
    }
}