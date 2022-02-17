package opencola.core.content

import org.apache.james.mime4j.dom.Header
import org.apache.james.mime4j.dom.field.ContentLocationField
import org.apache.james.mime4j.dom.field.ContentTypeField
import org.apache.james.mime4j.dom.field.UnstructuredField

fun Header.contentLocation(): ContentLocationField {
    // TODO: Is "US-ASCII" always right?
    return getField("Content-Location") as ContentLocationField
}

fun Header.contentType(): ContentTypeField {
    return getField("Content-Type") as ContentTypeField
}

fun Header.unstructuredField(name: String): UnstructuredField {
    return getField("Subject") as UnstructuredField
}