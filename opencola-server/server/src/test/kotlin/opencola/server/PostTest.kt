package opencola.server

import io.opencola.application.TestApplication
import opencola.server.handlers.Context
import opencola.server.handlers.EntityPayload
import opencola.server.handlers.newPost
import opencola.server.handlers.EntityResult
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PostTest {
    val app = TestApplication.instance

    @Test
    fun testNewPost() {
        val persona = app.getPersonas().first()
        val entityPayload = EntityPayload("", "Name", "https://image.com", "description", true, "tag", "comment")
        val result = newPost(app.inject(), app.inject(), app.inject(), app.inject(), app.inject(), Context(""), persona, entityPayload, emptySet())

        assertNotNull(result)

        val summary = result.summary
        assertEquals(summary.name, entityPayload.name)
        assertEquals(summary.imageUri, entityPayload.imageUri)
        assertEquals(summary.description, entityPayload.description)
        assertEquals(summary.uri, null)
        val activities = result.activities
        assertEquals(1, activities.size)

        val activity = activities[0]
        assertEquals(persona.personaId.toString(), activity.authorityId)

        val actions = activity.actions
        assertEquals(4, actions.size)
        assertContains(actions, EntityResult.Action(EntityResult.ActionType.bubble, null, null))
        assertContains(actions, EntityResult.Action(EntityResult.ActionType.like, null, entityPayload.like))
        assertContains(actions, EntityResult.Action(EntityResult.ActionType.tag, null, entityPayload.tags))
        assertEquals(actions.single() { it.actionType == EntityResult.ActionType.comment }.value, entityPayload.comment)
    }
}