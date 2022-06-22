package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class EventBusConfig(val name: String = "event-bus", val maxAttempts: Int = 3)
data class ServerConfig(val host: String, val port: Int)
data class KeystoreConfig(val name: String, val password: String)
data class SecurityConfig(val keystore: KeystoreConfig)
data class SolrConfig(val baseUrl: String, val connectionTimeoutMillis: Int, val socketTimeoutMillis: Int)
data class SearchConfig(val solr: SolrConfig?)
data class PeerConfig(val id: String, val publicKey: String, val name: String, val host: String, val active: Boolean = true)
data class NetworkConfig(val zeroTierProviderEnabled: Boolean = false, val peers: List<PeerConfig> = emptyList())

data class Config(
    val name: String,
    val eventBus: EventBusConfig,
    val server: ServerConfig,
    val security: SecurityConfig,
    val search: SearchConfig,
    val network: NetworkConfig,
)

fun Config.setName(name: String): Config {
    return Config(name, eventBus, server, security, search, network)
}

fun Config.setServer(server: ServerConfig): Config {
    return Config(name, eventBus, server, security, search, network)
}

fun Config.setNetwork(network: NetworkConfig): Config {
    return Config(name, eventBus, server, security, search, network)
}

fun Config.setZeroTierIntegration(enabled: Boolean): Config {
    return setNetwork(NetworkConfig(enabled, this.network.peers))
}

fun loadConfig(configPath: Path): Config {
    return ConfigLoader().loadConfigOrThrow(configPath)
}


