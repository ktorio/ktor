package io.ktor.http

import io.ktor.util.*
import org.slf4j.*
import java.io.*
import java.nio.file.*
import kotlin.streams.*

private val contentTypesFileName = "mimelist.csv"

fun ContentType.Companion.defaultForFile(file: File) = ContentType.fromFileExtension(file.extension).selectDefault()
fun ContentType.Companion.defaultForFile(file: Path) = ContentType.fromFileExtension(file.extension).selectDefault()

internal actual fun loadMimes(): List<Pair<String, ContentType>> {
    val stream = ContentType::class.java.classLoader.getResourceAsStream(contentTypesFileName)
        ?: return logErrorAndReturnEmpty()

    return stream.bufferedReader().lines().asSequence().mapNotNull {
        val line = it.trim()
        if (line.isEmpty()) return@mapNotNull null

        val index = line.indexOf(',')
        val extension = line.substring(0, index)
        val mime = line.substring(index + 1)

        extension.removePrefix(".").toLowerCase() to mime.toContentType()
    }.toList()
}

private var logged = false

private fun <T> logErrorAndReturnEmpty(): List<T> {
    if (!logged) {
        LoggerFactory.getLogger(ContentType::class.qualifiedName).error("Resource $contentTypesFileName is missing")
        logged = true
    }

    return emptyList()
}
