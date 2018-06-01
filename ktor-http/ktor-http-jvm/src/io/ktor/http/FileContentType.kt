package io.ktor.http

import io.ktor.compat.*
import io.ktor.util.*
import org.slf4j.*
import java.io.*
import java.nio.file.*

fun ContentType.Companion.defaultForFileExtension(extension: String) = ContentType.fromFileExtension(extension).selectDefault()
fun ContentType.Companion.defaultForFilePath(path: String) = ContentType.fromFilePath(path).selectDefault()
fun ContentType.Companion.defaultForFile(file: File) = ContentType.fromFileExtension(file.extension).selectDefault()
fun ContentType.Companion.defaultForFile(file: Path) = ContentType.fromFileExtension(file.extension).selectDefault()

fun ContentType.Companion.fromFilePath(path: String): List<ContentType> {
    val slashIndex = path.lastIndexOfAny("/\\".toCharArray())
    val index = path.indexOf('.', startIndex = slashIndex + 1)
    if (index == -1)
        return emptyList()
    return fromFileExtension(path.substring(index + 1))
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

fun ContentType.fileExtensions() = extensionsByContentType[this]
        ?: extensionsByContentType[this.withoutParameters()]
        ?: emptyList()

private fun List<ContentType>.selectDefault(): ContentType {
    val contentType = firstOrNull() ?: ContentType.Application.OctetStream
    if (contentType.contentType == "text" && contentType.charset() == null) {
        return contentType.withCharset(Charsets.UTF_8)
    }
    return contentType
}

private val contentTypesFileName = "mimelist.csv"

private val contentTypesByExtensions: Map<String, List<ContentType>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val records = processRecords { ext, contentType -> ext to contentType }
    caseInsensitiveMap<List<ContentType>>(records.size).apply { putAll(records) }
}

private val extensionsByContentType: Map<ContentType, List<String>> by lazy {
    processRecords { ext, contentType -> contentType to ext }
}

private fun <A, B> processRecords(operation: (String, ContentType) -> Pair<A, B>): Map<A, List<B>> {
    val stream = ContentType::class.java.classLoader.getResourceAsStream(contentTypesFileName) ?: return logErrorAndReturnEmpty()
    return stream.bufferedReader().useLines { lines ->
        lines.mapNotNull {
            val line = it.trim()
            if (!line.isEmpty()) {
                val index = line.indexOf(',')
                val extension = line.substring(0, index)
                val mime = line.substring(index + 1)
                operation(extension.removePrefix(".").toLowerCase(), mime.toContentType())
            } else
                null
        }.groupByPairs()
    }
}

private var logged = false
private fun <A, B> logErrorAndReturnEmpty(): Map<A, B> {
    if (!logged) {
        LoggerFactory.getLogger(ContentType::class.qualifiedName).error("Resource $contentTypesFileName is missing")
        logged = true
    }
    return emptyMap()
}

private fun <A, B> Sequence<Pair<A, B>>.groupByPairs() = groupBy { it.first }.mapValues { it.value.map { it.second } }
private fun String.toContentType() =
        try {
            ContentType.parse(this)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Failed to parse $this", e)
        }

