package opencola.server

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.extensions.toHexString
import opencola.core.model.Id
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import opencola.service.EntityService
import org.kodein.di.instance
import kotlin.io.path.Path

fun onServerStarted(application: Application){
    val entityService by application.injector.instance<EntityService>()
    application.logger.info { "Node Started: ${entityService.authority.authorityId}" }
}

fun getServer(application: Application): NettyApplicationEngine {
    val serverConfig = application.config.server ?: throw RuntimeException("Server config not specified")

    return embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        // install(CallLogging)
        configureHTTP()
        configureContentNegotiation()
        configureRouting(application)
        this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
    }
}

fun main() {
    val applicationPath = Path(System.getProperty("user.dir"))
    val config = loadConfig(applicationPath.resolve( "opencola-server.yaml"))
    val publicKey = Application.getOrCreateRootPublicKey(applicationPath.resolve(config.storage.path), config.security)

    val application = Application.instance(Application.getStoragePath(applicationPath, config),  config, publicKey)
    application.logger.info("Authority: ${Id.ofPublicKey(publicKey)}")
    application.logger.info("Public Key : ${publicKey.encoded.toHexString()}")

    // TODO: Make sure entityService starts as soon as server is up, so that transactions can be received

    getServer(application).start(wait = true)
}
