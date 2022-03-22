package opencola.server

import io.ktor.application.*
import io.ktor.server.netty.*
import opencola.core.TestApplication
import opencola.core.config.*
import opencola.core.config.Application
import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.service.EntityService
import opencola.service.search.SearchService
import org.junit.Test
import org.kodein.di.instance
import java.lang.Thread.sleep
import java.net.URI

class PeerTransactionTest {
    private val basePortNumber: Int = 6000
    private val baseConfig = TestApplication.config

    private fun getApplications(nServers: Int): List<Application> {
        return getApplications(nServers, baseConfig, basePortNumber)
    }

    fun startServer(engine: NettyApplicationEngine){
        // TODO: This is horrible. Figure out how to do this propery with suspend / coroutine / etc..
        var started = false
        engine.environment.monitor.subscribe(ApplicationStarted) { started = true }
        engine.start()
        while(!started){
            sleep(100)
        }
    }

    @Test
    fun testTransactionReplication(){
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it) }

        // Start first server and add a resource to the store
        startServer(server0)
        val authority0 by application0.injector.instance<Authority>()
        val resource0 = ResourceEntity(authority0.authorityId, URI("http://www.opencola.org"), "document 1", text="stuff")
        val entityStore0 by application0.injector.instance<EntityStore>()
        entityStore0.updateEntities(resource0)

        // Verify retrieval of transaction on startup via search
        startServer(server1)
        sleep(1000) // TODO Bad - after event bus is implemented, trigger off events, rather than waiting for sync
        val searchService1 by application1.injector.instance<SearchService>()
        val results0 = searchService1.search("stuff")
        assert(results0.matches.size == 1)
        assert(results0.matches[0].name == resource0.name)

        // Verify entity update triggers live replication
        val resource1 = ResourceEntity(authority0.authorityId, URI("http://www.opencola.org/page"), "document 2", text = "other stuff")
        val entityService0 by application0.injector.instance<EntityService>()
        entityService0.updateEntities(resource1)
        sleep(1000)
        val results1 = searchService1.search("other")
        assert(results1.matches.size == 1)
        assert(results1.matches[0].name == resource1.name)
    }
}