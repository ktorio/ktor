package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.file.*
import java.util.logging.*

fun ContentType.Companion.defaultForFileExtension(extension: String) = ContentType.fromFileExtension(extension).selectDefault()
fun ContentType.Companion.defaultForFilePath(path: String) = ContentType.fromFilePath(path).selectDefault()
fun ContentType.Companion.defaultForFile(file: File) = ContentType.fromFileExtension(file.extension).selectDefault()
fun ContentType.Companion.defaultForFile(file: Path) = ContentType.fromFileExtension(file.extension()).selectDefault()

fun ContentType.Companion.fromFilePath(path: String): List<ContentType> {
    val slashIndex = path.lastIndexOfAny("/\\".toCharArray())
    val index = path.indexOf('.', startIndex = slashIndex + 1)
    if (index == -1)
        return emptyList()
    return fromFileExtension(path.substring(index + 1))
}

private fun List<ContentType>.selectDefault(): ContentType {
    val contentType = firstOrNull() ?: ContentType.Application.OctetStream
    if (contentType.contentType == "text" && contentType.charset() == null) {
        return contentType.withCharset(Charsets.UTF_8)
    }
    return contentType
}

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

fun ContentType.fileExtensions() =
        extensionsByContentType[this]
                ?: extensionsByContentType[this.withoutParameters()]
                ?: emptyList<String>()

private val contentTypesFileName = "mimelist.csv"

private val contentTypesByExtensions: Map<String, List<ContentType>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    processRecords { ext, contentType -> ext to contentType }.let { records -> CaseInsensitiveMap<List<ContentType>>(records.size).apply { putAll(records) } }
}

private val extensionsByContentType: Map<ContentType, List<String>> by lazy {
    processRecords { ext, contentType -> contentType to ext }
}

private fun <A, B> processRecords(operation: (String, ContentType) -> Pair<A, B>) =
        ContentType::class.java.classLoader.getResourceAsStream(contentTypesFileName)?.bufferedReader()?.useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                val (ext, mime) = line.splitCSVPair()

                operation(ext.removePrefix(".").toLowerCase(), mime.asContentType())
            }.groupByPairs()
        } ?: logErrorAndReturnEmpty<A, List<B>>()


private var logged = false
private fun <A, B> logErrorAndReturnEmpty(): Map<A, B> {
    // TODO logging
    if (!logged) {
        Logger.getLogger(ContentType::class.qualifiedName).severe { "Resource $contentTypesFileName is missing" }
        logged = true
    }
    return emptyMap()
}

/**
 * splits line of CSV file, doesn't respect CSV escaping
 */
private fun String.splitCSVPair(): Pair<String, String> {
    val index = indexOf(',')
    return substring(0, index) to substring(index + 1)
}

private fun <A, B> Sequence<Pair<A, B>>.groupByPairs() = groupBy { it.first }.mapValues { it.value.map { it.second } }
private fun String.asContentType() =
        try {
            ContentType.parse(this)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Failed to parse $this", e)
        }

