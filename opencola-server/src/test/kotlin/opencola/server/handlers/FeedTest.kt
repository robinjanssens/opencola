package opencola.server.handlers

import opencola.core.TestApplication
import org.junit.Test

class FeedTest {
    @Test
    fun testFeedWithNoResults(){
        val app = TestApplication.instance
        getEntityResults(app.inject(), app.inject(), app.inject(), emptySet())
    }
}