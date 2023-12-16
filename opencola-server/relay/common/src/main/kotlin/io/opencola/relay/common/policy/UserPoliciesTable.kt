package io.opencola.relay.common.policy

import io.opencola.model.Id
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

object UserPolicies : LongIdTable() {
    val authorityId = binary("authorityId", 32)
    val userId = binary("userId", 32).uniqueIndex()
    // In SQLite, at least, onDelete = ReferenceOption.RESTRICT did not stop deletion of a reference policy,
    // so we handle manually.
    val policyId = long("policyId").references(Policies.id)

    val editTimeMilliseconds = long("editTimeMilliseconds")
}

class UserPolicyRow(private val resultRow: ResultRow) {
    val id: Long by lazy { resultRow[UserPolicies.id].value }
    val authorityId: Id by lazy { Id(resultRow[UserPolicies.authorityId]) }
    val userId: Id by lazy { Id(resultRow[UserPolicies.userId]) }
    val policyId: Long by lazy { resultRow[UserPolicies.policyId] }
    val editTimeMilliseconds: Long by lazy { resultRow[UserPolicies.editTimeMilliseconds] }
}