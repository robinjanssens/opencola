package opencola.core.storage

import io.opencola.application.TestApplication
import io.opencola.content.MhtmlPage
import io.opencola.content.parseMime
import io.opencola.model.Actions
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.cache.MhtCache
import opencola.server.handlers.updateResource
import org.junit.Test
import org.kodein.di.instance
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.assertNotNull

class MhtCacheTest {
    val injector = TestApplication.instance.injector

    @Test
    fun testReadFromCache(){
        val persona = TestApplication.instance.getPersonas().first()
        val mhtCache by injector.instance<MhtCache>()
        val entityStore by injector.instance<EntityStore>()
        val fileStore by injector.instance<ContentAddressedFileStore>()

        val rootPath = Path(System.getProperty("user.dir"),"../..", "sample-docs").toString()

        val path = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
        val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
        val mhtmlPage = MhtmlPage(message)

        val entity = updateResource(persona.personaId, entityStore, fileStore, mhtmlPage, Actions())
        val data = mhtCache.getData(entity.entityId, persona.personaId)
        assertNotNull(data)

        val part0 = mhtCache.getDataPart(persona.personaId, entity.entityId, "0.html")
        assertNotNull(part0)
    }
}