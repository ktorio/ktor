package org.jetbrains.ktor.http

import org.jetbrains.ktor.util.*
import java.io.*

class ContentDisposition(val disposition: String, val parameters: ValuesMap) {
    val name: String?
        get() = parameters["name"]

    override fun toString(): String {
        return (listOf(disposition) + parameters.entries().flatMap { it.value.map { e -> it.key to e } }.map { "${it.first}=${it.second}" }).joinToString("; ") // TODO we should be able to escape values if needed
    }

    companion object {
        fun parse(value: String) = parseHeaderValue(value).let { preParsed ->
            ContentDisposition(preParsed.single().value,
                    parameters = ValuesMap(preParsed.single().params.groupBy({ it.name }, { it.value }), true)
            )
        }
    }
}

sealed class PartData(open val dispose: () -> Unit, open val partHeaders: ValuesMap) {
    class FormItem(val value: String, override val dispose: () -> Unit, override val partHeaders: ValuesMap) : PartData(dispose, partHeaders)
    class FileItem(val streamProvider: () -> InputStream, override val dispose: () -> Unit, override val partHeaders: ValuesMap) : PartData(dispose, partHeaders) {
        val originalFileName = contentDisposition?.parameters?.get("filename")
    }

    val contentDisposition: ContentDisposition? by lazy {
        partHeaders[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
    }

    val name: String?
        get() = contentDisposition?.name

    val contentType: ContentType? by lazy { partHeaders[HttpHeaders.ContentType]?.let { ContentType.parse(it) } }
}

interface MultiPartData {
    val isMultipart: Boolean
    val parts: Sequence<PartData>
    // TODO think of possible async methods
}

fun <T> Iterable<T>.groupBy(key: (T) -> String, value: (T) -> String): Map<String, List<String>> =
        groupBy(key).mapValues { it.value.map(value) }

