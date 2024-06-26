/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.content

import io.opencola.util.tryParseUri
import org.apache.james.mime4j.dom.*
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

// TODO: Make interface
fun parseMime(inputStream: InputStream): Message? {
    // TODO: Think about a .thread extension that allows for a chain of operations on an original value
    val defaultMessageBuilder = DefaultMessageBuilder()
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    return defaultMessageBuilder.parseMessage(inputStream)
}

fun parseMime(mimeText: String) : Message? {
    return ByteArrayInputStream(mimeText.toByteArray()).use { parseMime(it) }
}

fun transformTextContent(body: Body, textTransform: (String) -> String): ByteArray {
    return when (body) {
        is TextBody -> textTransform(String(body.inputStream.readAllBytes(), Charsets.UTF_8)).toByteArray()
        is BinaryBody -> body.inputStream.use { it.readBytes() }
        else -> throw RuntimeException("Unhandled body type")
    }
}

// TODO: Read from config. Should error on types not found in ContentType
private val extensionMap = mapOf("svg+xml" to "svg")

fun normalizeExtension(extension: String?): String {
    return when (extension) {
        null -> ""
        else -> ".${extensionMap[extension] ?: extension}"
    }
}

data class Part(val name: String, val mimeType: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Part

        if (name != other.name) return false
        if (mimeType != other.mimeType) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

fun getBodyParts(message: Message): MutableList<Entity> {
    val multipart = message.body as? Multipart ?: throw RuntimeException("Cannot get body parts of a message that is not Multipart")
    val bodyParts = multipart.bodyParts

    val header = bodyParts.first().header
    if(bodyParts.isEmpty() || header.contentType()?.subType != "html" || header.contentLocation() == null){
        throw RuntimeException("First body part of Mht message must be of type 'html' and have a location")
    }

    return bodyParts
}

fun splitMht(message: Message): List<Part> {
    // TODO: Investigate: It makes no sense, but some docs have parts with no content location or duplicate locations
    val partsWithLocation = getBodyParts(message)
        .filter { it.header.contentLocation() != null }
        .distinctBy { it.header.contentLocation()!!.location }

    val locationMap = partsWithLocation.mapIndexed { index, part ->
        val filename = "$index${normalizeExtension(part.header.contentType()?.subType)}"
        val location = part.header.contentLocation()!!.location
        Pair(location, filename)
    }.toMap()

    val parts = partsWithLocation.map {
        val content = transformTextContent(it.body) { location ->
            // Drop first part (root html) so that we don't replace the root url anywhere
            locationMap.entries.drop(1).fold(location) { acc, entry ->
                acc.replace(entry.key, entry.value)
            }
        }
        val location = locationMap[it.header.contentLocation()!!.location] as String
        Part(location, it.header.contentType()?.mimeType ?: "application/octet-stream", content)
    }

    return parts
}

fun scoreImage(size: Int): Double {
    return size.toDouble()
}

fun getImageUri(message: Message): URI? {
    return getBodyParts(message)
        .asSequence()
        .filter { it.header.contentLocation()?.location?.tryParseUri()?.isAbsolute != null }
        .filter { it.header.contentType()?.mediaType?.lowercase() == "image" }
        .filter { it.body is BinaryBody }
        .mapIndexed { _, v -> Pair(scoreImage((v.body as BinaryBody).inputStream.available()), v) }
        .sortedByDescending { (score, _) -> score } // Pick the biggest image - not always best?
        .map { (_, v) -> v }
        .mapNotNull { it.header.contentLocation()!!.location.tryParseUri() }
        .firstOrNull()
}
