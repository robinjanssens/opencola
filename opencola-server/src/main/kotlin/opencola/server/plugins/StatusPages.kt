package opencola.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.server.ErrorResponse

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val response = ErrorResponse(cause.message ?: "Unknown")
            call.respond(HttpStatusCode.InternalServerError, Json.encodeToString(response))
        }
    }
}
