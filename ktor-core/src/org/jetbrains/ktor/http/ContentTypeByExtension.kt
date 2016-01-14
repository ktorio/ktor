package org.jetbrains.ktor.http

import java.io.*


object ContentTypeByExtension {
    private val contentTypesFileName = "mimelist.csv"

    val contentTypesByExtensions: Map<String, List<ContentType>> by lazy {
        processRecords { ext, contentType -> ext to contentType }
    }

    private val extensionsByContentType: Map<ContentType, List<String>> by lazy {
        processRecords { ext, contentType -> contentType to ext }
    }

    // TODO handle windows path as well
    fun lookupByPath(path: String) = lookupByExtension(path.substringAfterLast("/", path).substringAfter("."))

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

    private inline fun <A, B> processRecords(crossinline operation: (String, ContentType) -> Pair<A, B>) =
            ContentTypeByExtension::class.java.classLoader.getResourceAsStream(contentTypesFileName)?.bufferedReader()?.useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                    val (ext, mime) = line.splitCSVPair()

                    operation(ext.removePrefix(".").toLowerCase(), mime.asContentType())
                }.groupByPairs()
            } ?: throw IOException("Resource $contentTypesFileName is missing")

    // TODO think of mime parameters that could prevent us from finding mapping
    private fun lookupByContentType(type: ContentType) = extensionsByContentType[type] ?: emptyList<String>()
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
