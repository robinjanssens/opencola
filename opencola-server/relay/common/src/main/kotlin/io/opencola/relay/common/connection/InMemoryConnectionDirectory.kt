package io.opencola.relay.common.connection

import io.opencola.model.Id
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class InMemoryConnectionDirectory(val localUri: URI) : ConnectionDirectory {
    private val logger = KotlinLogging.logger("InMemoryConnectionDirectory")
    private val connections = ConcurrentHashMap<Id, ConnectionEntry>()

    override fun add(connection: Connection): ConnectionEntry {
        return ConnectionEntry(localUri, connection).also { connections[connection.id] = it }
    }

    override fun get(id: Id): ConnectionEntry? {
        val entry = connections[id] ?: return null

        if (!runBlocking { entry.connection.isReady() }) {
            logger.info { "Removing not ready connection to $id" }
            connections.remove(id)
            return null
        }

        return entry
    }

    override fun remove(id: Id) {
        connections.remove(id)
    }

    override fun closeAll() {
        runBlocking {
            connections.values.forEach { it.connection.close() }
            connections.clear()
        }
    }

    suspend fun states(): List<Pair<String, Boolean>> {
        return connections.map { Pair(it.key.toString(), it.value.connection.isReady()) }
    }
}