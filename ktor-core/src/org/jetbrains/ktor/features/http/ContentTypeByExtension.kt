package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.http.*
import java.util.logging.*

object ContentTypeByExtension {
    private val contentTypesFileName = "mimelist.csv"

    val contentTypesByExtensions: Map<String, List<ContentType>> by lazy {
        processRecords { ext, contentType -> ext to contentType }
    }

    private val extensionsByContentType: Map<ContentType, List<String>> by lazy {
        processRecords { ext, contentType -> contentType to ext }
    }

    fun lookupByPath(path: String): List<ContentType> {
        val slashIndex = path.lastIndexOfAny("/\\".toCharArray())
        val index = path.indexOf('.', startIndex = slashIndex + 1)
        return lookupByExtension(path.substring(index + 1))
    }

    fun lookupByExtension(ext: String): List<ContentType> {
        var current = ext.removePrefix(".").toLowerCase()
        while (current.isNotEmpty()) {
            val type = contentTypesByExtensions[current]
            if (type != null) {
                return type
            }
            current = current.substringAfter(".", "")
        }

        return emptyList()
    }

    fun lookupByContentType(type: ContentType) =
            extensionsByContentType[type]
                    ?: extensionsByContentType[type.withoutParameters()]
                    ?: emptyList<String>()

    private fun <A, B> processRecords(operation: (String, ContentType) -> Pair<A, B>) =
            ContentTypeByExtension::class.java.classLoader.getResourceAsStream(contentTypesFileName)?.bufferedReader()?.useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                    val (ext, mime) = line.splitCSVPair()

                    operation(ext.removePrefix(".").toLowerCase(), mime.asContentType())
                }.groupByPairs()
            } ?: logErrorAndReturnEmpty<A, List<B>>()


    private var logged = false
    private fun <A, B> logErrorAndReturnEmpty(): Map<A, B> {
        // TODO logging
        if (!logged) {
            Logger.getLogger(ContentTypeByExtension::class.qualifiedName).severe { "Resource ${contentTypesFileName} is missing" }
            logged = true
        }
        return emptyMap()
    }
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
