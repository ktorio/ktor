/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.date.GMTDate
import java.io.*
import java.net.*
import java.nio.file.*
import kotlin.io.path.*

/**
 * Supported pre compressed file types and associated extensions
 *
 * **See Also:** [Accept-Encoding](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.CompressedFileType)
 */
public enum class CompressedFileType(public val extension: String, public val encoding: String = extension) {
    ZSTD("zst", "zstd"),
    BROTLI("br"),
    GZIP("gz", "gzip"),
    DEFLATE("deflate"),
}

internal val compressedKey = AttributeKey<List<CompressedFileType>>("StaticContentCompressed")

internal val Route.staticContentEncodedTypes: List<CompressedFileType>?
    get() = attributes.getOrNull(compressedKey) ?: parent?.staticContentEncodedTypes

internal class PreCompressedResponse(
    private val original: ReadChannelContent,
    private val compressedType: CompressedFileType?,
) : OutgoingContent.ReadChannelContent() {
    override val contentLength get() = original.contentLength
    override val contentType get() = original.contentType
    override val status get() = original.status
    override val headers by lazy(LazyThreadSafetyMode.NONE) { original.preCompressedHeaders(compressedType) }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

    override fun readFrom() = original.readFrom()
    override fun readFrom(range: LongRange) = original.readFrom(range)
}

internal class PreCompressedBytesResponse(
    private val original: ByteArrayContent,
    private val compressedType: CompressedFileType?,
) : OutgoingContent.ByteArrayContent() {
    override val contentLength get() = original.contentLength
    override val contentType get() = original.contentType
    override val status get() = original.status
    override val headers by lazy(LazyThreadSafetyMode.NONE) { original.preCompressedHeaders(compressedType) }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

    override fun bytes(): ByteArray = original.bytes()
}

private fun OutgoingContent.preCompressedHeaders(compressedType: CompressedFileType?): Headers {
    if (compressedType == null) return headers

    return Headers.build {
        appendFiltered(headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
        append(HttpHeaders.ContentEncoding, compressedType.encoding)

        set(
            HttpHeaders.Vary,
            headers[HttpHeaders.Vary]?.plus(", ${HttpHeaders.AcceptEncoding}") ?: HttpHeaders.AcceptEncoding
        )
    }
}

internal fun bestCompressionFit(
    file: File,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: Array<CompressedFileType>
): Pair<File, CompressedFileType>? {
    val acceptedEncodings = acceptEncoding.mapTo(LinkedHashSet(acceptEncoding.size)) { it.value }

    // Find the smallest file in the accepted encodings
    var smallestType: CompressedFileType? = null
    var smallestFile: File? = null
    var smallestSize: Long = Long.MAX_VALUE

    if (compressedTypes.isEmpty()) {
        return null
    }

    for (compressedType in compressedTypes) {
        if (compressedType.encoding !in acceptedEncodings) {
            continue
        }

        val compressedFile = File("${file.absolutePath}.${compressedType.extension}")

        if (!compressedFile.isFile) {
            continue
        }

        val compressedSize = compressedFile.length()

        if (smallestSize > compressedSize) {
            smallestType = compressedType
            smallestFile = compressedFile
            smallestSize = compressedSize
        }
    }

    return (smallestFile ?: return null) to (smallestType ?: return null)
}

internal fun bestCompressionFit(
    fileSystem: FileSystemPaths,
    path: Path,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: Array<CompressedFileType>
): Pair<Path, CompressedFileType>? {
    val acceptedEncodings = acceptEncoding.mapTo(HashSet(acceptEncoding.size)) { it.value }

    // Find the smallest file in the accepted encodings
    var smallestType: CompressedFileType? = null
    var smallestPath: Path? = null
    var smallestSize: Long = Long.MAX_VALUE

    for (compressedType in compressedTypes) {
        if (compressedType.encoding !in acceptedEncodings) {
            continue
        }

        val compressedPath = fileSystem.getPath("${path.pathString}.${compressedType.extension}")

        if (!compressedPath.isRegularFile()) {
            continue
        }

        val compressedSize = compressedPath.fileSize()

        if (smallestSize > compressedSize) {
            smallestType = compressedType
            smallestPath = compressedPath
            smallestSize = compressedSize
        }
    }

    return (smallestPath ?: return null) to (smallestType ?: return null)
}

internal fun bestCompressionFit(
    acceptEncoding: List<HeaderValue>,
    compressedFiles: Array<Pair<CachedStaticFile, CompressedFileType>>
): Pair<CachedStaticFile, CompressedFileType>? {
    val acceptedEncodings = acceptEncoding.mapTo(HashSet(acceptEncoding.size)) { it.value }

    // Find the smallest file in the accepted encodings
    var smallest: Pair<CachedStaticFile, CompressedFileType>? = null
    var smallestSize: Int = Int.MAX_VALUE

    for (compressedFile in compressedFiles) {
        val (file, compressedType) = compressedFile

        if (compressedType.encoding !in acceptedEncodings) {
            continue
        }

        if (smallestSize > file.bytes.size) {
            smallest = compressedFile
            smallestSize = file.bytes.size
        }
    }

    return smallest
}

internal class CompressedResource(
    val url: URL,
    val content: OutgoingContent.ReadChannelContent,
    val compression: CompressedFileType
)

internal fun bestCompressionFit(
    call: ApplicationCall,
    resource: String,
    packageName: String?,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: Array<CompressedFileType>,
    contentType: (URL) -> ContentType
): CompressedResource? {
    val acceptedEncodings = acceptEncoding.mapTo(HashSet(acceptEncoding.size)) { it.value }
    // We respect the order in compressedTypes, not the one in Accept header

    if (compressedTypes.isEmpty()) {
        return null
    }

    for (compressedType in compressedTypes) {
        if (compressedType.encoding !in acceptedEncodings) {
            continue
        }

        val compressed = "$resource.${compressedType.extension}"
        val resolved = call.application.resolveResource(compressed, packageName) { url ->
            val requestPath = url.path.replace(
                Regex("${Regex.escapeReplacement(compressed.substringAfterLast(File.separator))}$"),
                resource.substringAfterLast(File.separator)
            )
            contentType(URL(url.protocol, url.host, url.port, requestPath))
        } ?: continue

        return CompressedResource(resolved.first, resolved.second, compressedType)
    }

    return null
}

internal suspend fun ApplicationCall.respondStaticFile(
    requestedFile: File,
    compressedTypes: Array<CompressedFileType>,
    contentType: (File) -> ContentType = { ContentType.defaultForFile(it) },
    cacheControl: (File) -> List<CacheControl> = { emptyList() },
    lastModified: (File) -> GMTDate? = { null },
    etag: ETagProvider = ETagProvider { null },
    modify: suspend (File, ApplicationCall) -> Unit = { _, _ -> }
) {
    if (!requestedFile.isFile) {
        return
    }

    attributes.put(StaticFileLocationProperty, requestedFile.path)

    val responseContentType = contentType(requestedFile)
    val cacheControlValues = cacheControl(requestedFile).joinToString(", ")

    response.addCacheControlHeader(cacheControlValues)

    val bestCompressionFit = bestCompressionFit(requestedFile, request.acceptEncodingItems(), compressedTypes)

    if (bestCompressionFit == null) {
        modify(requestedFile, this)

        val content = LocalFileContent(requestedFile, responseContentType)
            .provideVersions(etag, lastModified, requestedFile)

        respond(content)
    } else {
        suppressCompression()

        modify(requestedFile, this)

        val (compressedFile, compression) = bestCompressionFit

        val localFileContent = LocalFileContent(compressedFile, responseContentType)
            .provideVersions(etag, lastModified, compressedFile)

        respond(PreCompressedResponse(localFileContent, compression))
    }
}

internal suspend fun ApplicationCall.respondStaticPath(
    fileSystem: FileSystemPaths,
    requestedPath: Path,
    compressedTypes: Array<CompressedFileType>,
    contentType: (Path) -> ContentType = { ContentType.defaultForPath(it) },
    cacheControl: (Path) -> List<CacheControl> = { emptyList() },
    modify: suspend (Path, ApplicationCall) -> Unit = { _, _ -> },
    lastModified: (Path) -> GMTDate? = { null },
    etag: ETagProvider = ETagProvider { null },
) {
    if (!requestedPath.exists()) {
        return
    }

    attributes.put(StaticFileLocationProperty, requestedPath.toString())

    val responseContentType = contentType(requestedPath)
    val cacheControlValues = cacheControl(requestedPath).joinToString(", ")

    response.addCacheControlHeader(cacheControlValues)

    val bestCompressionFit =
        bestCompressionFit(fileSystem, requestedPath, request.acceptEncodingItems(), compressedTypes)

    if (bestCompressionFit == null) {
        modify(requestedPath, this)

        val content = LocalPathContent(requestedPath, responseContentType)
            .provideVersions(etag, lastModified, requestedPath)

        respond(content)
    } else {
        suppressCompression()

        modify(requestedPath, this)

        val (compressedPath, compression) = bestCompressionFit

        val localFileContent = LocalPathContent(compressedPath, responseContentType)
            .provideVersions(etag, lastModified, compressedPath)

        respond(PreCompressedResponse(localFileContent, compression))
    }
}

internal suspend fun ApplicationCall.respondCachedStaticPath(
    requestedPath: Path,
    cachedFile: CachedStaticFile,
    cachedCompressedFiles: Array<Pair<CachedStaticFile, CompressedFileType>>,
    modify: suspend (Path, ApplicationCall) -> Unit = { _, _ -> }
) {
    attributes.put(StaticFileLocationProperty, requestedPath.toString())

    val cacheControlValues = cachedFile.cacheControl.joinToString(", ")

    response.addCacheControlHeader(cacheControlValues)

    val bestCompressionFit = bestCompressionFit(request.acceptEncodingItems(), cachedCompressedFiles)

    if (bestCompressionFit == null) {
        modify(requestedPath, this)

        val content = ByteArrayContent(cachedFile.bytes, cachedFile.contentType)
            .provideVersions(cachedFile.etag, cachedFile.lastModified)

        respond(content)
    } else {
        suppressCompression()

        modify(requestedPath, this)

        val (compressedFile, compression) = bestCompressionFit

        val localFileContent = ByteArrayContent(compressedFile.bytes, compressedFile.contentType)
            .provideVersions(compressedFile.etag, compressedFile.lastModified)

        respond(PreCompressedBytesResponse(localFileContent, compression))
    }
}

internal suspend fun ApplicationCall.respondStaticResource(
    requestedResource: String,
    packageName: String?,
    compressedTypes: Array<CompressedFileType>,
    contentType: (URL) -> ContentType = { ContentType.defaultForFileExtension(it.path.extension()) },
    cacheControl: (URL) -> List<CacheControl> = { emptyList() },
    modifier: suspend (URL, ApplicationCall) -> Unit = { _, _ -> },
    lastModified: (URL) -> GMTDate? = { null },
    etag: ETagProvider = ETagProvider { null },
) {
    attributes.put(StaticFileLocationProperty, requestedResource)

    val bestCompressionFit = bestCompressionFit(
        call = this,
        resource = requestedResource,
        packageName = packageName,
        acceptEncoding = request.acceptEncodingItems(),
        compressedTypes = compressedTypes,
        contentType = contentType
    )

    if (bestCompressionFit == null) {
        val content = application.resolveResource(
            path = requestedResource,
            resourcePackage = packageName,
            mimeResolve = contentType
        )

        if (content != null) {
            val cacheControlValues = cacheControl(content.first).joinToString(", ")

            response.addCacheControlHeader(cacheControlValues)

            modifier(content.first, this)

            val outgoingContent = content.second.provideVersions(etag, lastModified, content.first)
            respond(outgoingContent)
        }
    } else {
        suppressCompression()

        val cacheControlValues = cacheControl(bestCompressionFit.url).joinToString(", ")

        response.addCacheControlHeader(cacheControlValues)

        modifier(bestCompressionFit.url, this)

        val content = PreCompressedResponse(bestCompressionFit.content, bestCompressionFit.compression)
            .provideVersions(etag, lastModified, bestCompressionFit.url)

        respond(content)
    }
}

private fun <Resource : Any, Content : OutgoingContent> Content.provideVersions(
    etag: ETagProvider,
    lastModified: (Resource) -> GMTDate?,
    resource: Resource,
): Content = provideVersions(etag.provide(resource), lastModified(resource))

private fun <Content : OutgoingContent> Content.provideVersions(
    etag: EntityTagVersion?,
    lastModified: GMTDate?,
): Content {
    val newVersions = versions.toMutableList()
    if (etag != null) newVersions.add(etag)
    if (lastModified != null) newVersions.add(LastModifiedVersion(lastModified))
    versions = newVersions
    return this
}

private fun ApplicationResponse.addCacheControlHeader(cacheControlValues: String) {
    if (cacheControlValues.isNotEmpty()) {
        header(HttpHeaders.CacheControl, cacheControlValues)
    }
}
