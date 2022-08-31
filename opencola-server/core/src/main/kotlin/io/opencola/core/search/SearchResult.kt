package io.opencola.core.search

import io.opencola.core.model.Id

// TODO: Add highlighting
// TODO: Should probably return full entity, but somewhat expensive, and won't be compatible with highlighting
class SearchResult(val authorityId: Id, val entityId: Id, val name: String?, val description: String?)