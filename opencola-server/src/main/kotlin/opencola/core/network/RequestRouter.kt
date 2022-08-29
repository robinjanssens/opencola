package opencola.core.network

import mu.KotlinLogging

class Route(val method: Request.Method, val path: String, val handler: (Request) -> Response)

// TODO: This should be wrapped by a RequestRouter that marks nodes as online upon successful request
// TODO: Rename requestHandler?
class RequestRouter(private val routes: List<Route>) {
    private val logger = KotlinLogging.logger("RequestRouter")

    fun handleRequest(request: Request) : Response {
        val handler = routes.firstOrNull { it.method == request.method && it.path == request.path }?.handler
            ?: "No handler specified for ${request.method} ${request.path}".let {
                logger.error { it }
                return Response(404, it, null)
            }

        return try{
            handler(request)
        } catch (e: Exception){
            logger.error { "Handler encountered error: $e" }
            // TODO: What should be exposed to caller?
            Response(500, e.toString(), null)
        }
    }
}