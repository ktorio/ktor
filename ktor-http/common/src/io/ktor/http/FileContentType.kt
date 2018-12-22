package io.ktor.http

import io.ktor.util.*
import kotlinx.io.charsets.*

/**
 * Default [ContentType] for [extension]
 */
fun ContentType.Companion.defaultForFileExtension(extension: String): ContentType =
    ContentType.fromFileExtension(extension).selectDefault()

/**
 * Default [ContentType] for file [path]
 */
fun ContentType.Companion.defaultForFilePath(path: String): ContentType = ContentType.fromFilePath(path).selectDefault()

/**
 * Recommended content types by file [path]
 */
fun ContentType.Companion.fromFilePath(path: String): List<ContentType> {
    val slashIndex = path.lastIndexOfAny("/\\".toCharArray())
    val index = path.indexOf('.', startIndex = slashIndex + 1)
    if (index == -1)
        return emptyList()
    return fromFileExtension(path.substring(index + 1))
}

/**
 * Recommended content type by file name extension
 */
fun ContentType.Companion.fromFileExtension(ext: String): List<ContentType> {
    var current = ext.removePrefix(".")
    while (current.isNotEmpty()) {
        val type = contentTypesByExtensions[current]
        if (type != null) {
            return type
        }
        current = current.substringAfter(".", "")
    }

    return emptyList()
}

/**
 * Recommended file name extensions for this content type
 */
fun ContentType.fileExtensions(): List<String> = extensionsByContentType[this]
    ?: extensionsByContentType[this.withoutParameters()]
    ?: emptyList()

private val contentTypesByExtensions: Map<String, List<ContentType>> =
    caseInsensitiveMap<List<ContentType>>().apply { putAll(mimes.asSequence().groupByPairs()) }

private val extensionsByContentType: Map<ContentType, List<String>> =
    mimes.asSequence().map { (first, second) -> second to first }.groupByPairs()

internal fun List<ContentType>.selectDefault(): ContentType {
    val contentType = firstOrNull() ?: ContentType.Application.OctetStream
    return if (contentType.contentType == "text" && contentType.charset() == null)
        contentType.withCharset(Charsets.UTF_8) else contentType
}

internal fun <A, B> Sequence<Pair<A, B>>.groupByPairs() = groupBy { it.first }
    .mapValues { e -> e.value.map { it.second } }

internal fun String.toContentType() = try {
    ContentType.parse(this)
} catch (e: Throwable) {
    throw IllegalArgumentException("Failed to parse $this", e)
}
