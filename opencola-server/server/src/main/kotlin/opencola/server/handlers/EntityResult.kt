package opencola.server.handlers

import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import kotlinx.serialization.Serializable

@Serializable
// TODO - Replace Search Result
data class EntityResult(
    val entityId: String,
    val summary: Summary,
    val activities: List<Activity>
) {
    // TODO: Remove dataId at top level - now part of activity, so any version can be accessed
    constructor(entityId: Id, summary: Summary, activities: List<Activity>) : this(
        entityId.toString(),
        summary,
        activities
    )

    @Serializable
    data class Summary(val name: String?, val uri: String?, val description: String?, val imageUri: String?, val postedBy: String?, val postedByImageUri: String?)

    enum class ActionType() {
        Save,
        Trust,
        Like,
        Rate,
        Tag,
        Comment,
    }

    @Serializable
    data class Action(val type: String, val id: String?, val value: String?){
        constructor(type: ActionType, id: Id?, value: Any?) :
                this(type.name.lowercase(), id.nullOrElse { it.toString() }, value.nullOrElse { it.toString() })
    }


    @Serializable
    data class Activity(
        // TODO: Make Authority(id, name, host)
        val authorityId: String,
        val authorityName: String,
        val host: String,
        val epochSecond: Long,
        val actions: List<Action>,
    ) {
        constructor(authority: Authority, epochSecond: Long, actions: List<Action>) :
                this(
                    authority.entityId.toString(),
                    authority.name!!,
                    authority.uri!!.authority ?: "",
                    epochSecond,
                    actions)
    }
}