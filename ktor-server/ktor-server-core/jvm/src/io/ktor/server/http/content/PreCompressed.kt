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

/**
 * Chooses which pre-compressed variant to serve when multiple types configured via
 * [StaticContentConfig.preCompressed] are accepted by the client and exist.
 *
 * Implement this functional interface to customize selection. [FirstMatch] and [Smallest] are
 * provided as built-in strategies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.PreCompressedFileStrategy)
 */
public fun interface PreCompressedFileStrategy {
    /**
     * Selects one candidate from [candidates], or `null` to serve the uncompressed content instead.
     *
     * [candidates] is produced lazily in the order configured via [StaticContentConfig.preCompressed].
     * Consuming it without accessing [Candidate.size] (for example with `firstOrNull()`) avoids
     * computing the size of candidates that are never inspected.
     */
    public fun select(candidates: Sequence<Candidate>): Candidate?

    /**
     * A single accepted, existing pre-compressed variant considered by a [PreCompressedFileStrategy].
     * [size] is computed lazily the first time it's accessed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.PreCompressedFileStrategy.Candidate)
     */
    public class Candidate internal constructor(
        public val type: CompressedFileType,
        sizeProvider: () -> Long,
    ) {
        public val size: Long by lazy(LazyThreadSafetyMode.NONE, sizeProvider)
    }

    public companion object {
        /**
         * Serves the first configured, accepted, existing type. This matches the priority order
         * described in [StaticContentConfig.preCompressed] and is the default strategy.
         */
        public val FirstMatch: PreCompressedFileStrategy = PreCompressedFileStrategy { it.firstOrNull() }

        /**
         * Compares every accepted, existing variant by [Candidate.size] and serves the smallest.
         * Unlike [FirstMatch], this evaluates the size of every candidate.
         */
        public val Smallest: PreCompressedFileStrategy =
            PreCompressedFileStrategy { it.minByOrNull(Candidate::size) }
    }
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

internal data class AcceptEncoding(val value: String, val quality: Double)

/**
 * Parses the `Accept-Encoding` header only when precompressed content is actually configured,
 * avoiding an allocation on every request when [compressedTypes] is empty.
 */
internal fun ApplicationRequest.acceptedEncodings(compressedTypes: Array<CompressedFileType>): List<AcceptEncoding> {
    if (compressedTypes.isEmpty()) return emptyList()
    return acceptEncodingItems().map { AcceptEncoding(it.value, it.quality) }
}

internal fun bestCompressionFit(
    file: File,
    compressedTypes: Array<CompressedFileType>,
    acceptedEncodings: List<AcceptEncoding>,
    strategy: PreCompressedFileStrategy,
): Pair<File, CompressedFileType>? {
    if (compressedTypes.isEmpty()) {
        return null
    }

    val basePath = file.absolutePath

    val candidates = sequence {
        for (compressedType in compressedTypes) {
            if (acceptedEncodings.none {
                    it.quality > 0.0 && it.value.equals(compressedType.encoding, ignoreCase = true)
                }
            ) {
                continue
            }

            val compressedFile = File("$basePath.${compressedType.extension}")
            val compressedSize = compressedFile.length()

            // A missing file also reports length() == 0, and real compressed output is never
            // empty, so this doubles as the existence check without a separate isFile() stat.
            if (compressedSize <= 0) {
                continue
            }

            yield(PreCompressedFileStrategy.Candidate(compressedType) { compressedSize })
        }
    }

    val selected = strategy.select(candidates) ?: return null
    return File("$basePath.${selected.type.extension}") to selected.type
}

internal fun bestCompressionFit(
    fileSystem: FileSystemPaths,
    path: Path,
    compressedTypes: Array<CompressedFileType>,
    acceptedEncodings: List<AcceptEncoding>,
    strategy: PreCompressedFileStrategy,
): Pair<Path, CompressedFileType>? {
    if (compressedTypes.isEmpty()) {
        return null
    }

    val basePath = path.pathString

    val candidates = sequence {
        for (compressedType in compressedTypes) {
            if (acceptedEncodings.none {
                    it.quality > 0.0 && it.value.equals(compressedType.encoding, ignoreCase = true)
                }
            ) {
                continue
            }

            val compressedPath = fileSystem.getPath("$basePath.${compressedType.extension}")

            if (!compressedPath.isRegularFile()) {
                continue
            }

            yield(PreCompressedFileStrategy.Candidate(compressedType) { compressedPath.fileSize() })
        }
    }

    val selected = strategy.select(candidates) ?: return null
    return fileSystem.getPath("$basePath.${selected.type.extension}") to selected.type
}

internal fun <T : Any> bestCompressionFit(
    compressedFiles: Array<Pair<CachedStaticFile<T>, CompressedFileType>>,
    acceptEncoding: List<AcceptEncoding>,
    strategy: PreCompressedFileStrategy,
): Pair<CachedStaticFile<T>, CompressedFileType>? {
    if (compressedFiles.isEmpty()) {
        return null
    }

    val candidates = sequence {
        for (compressedFile in compressedFiles) {
            val (file, compressedType) = compressedFile

            if (acceptEncoding.none {
                    it.quality > 0.0 && it.value.equals(compressedType.encoding, ignoreCase = true)
                }
            ) {
                continue
            }

            yield(PreCompressedFileStrategy.Candidate(compressedType) { file.bytes.size.toLong() })
        }
    }

    val selected = strategy.select(candidates) ?: return null
    return compressedFiles.first { it.second == selected.type }
}

internal class CompressedResource(
    val url: URL,
    val content: OutgoingContent.ReadChannelContent,
    val compression: CompressedFileType
)

internal fun bestCompressionFit(
    call: ApplicationCall,
    resource: String,
    compressedTypes: Array<CompressedFileType>,
    acceptedEncodings: List<AcceptEncoding>,
    contentType: (URL) -> ContentType,
    strategy: PreCompressedFileStrategy,
): CompressedResource? {
    if (compressedTypes.isEmpty()) {
        return null
    }

    val resolvedByType = HashMap<CompressedFileType, Pair<URL, OutgoingContent.ReadChannelContent>>()

    val candidates = sequence {
        for (compressedType in compressedTypes) {
            if (acceptedEncodings.none {
                    it.quality > 0.0 && it.value.equals(compressedType.encoding, ignoreCase = true)
                }
            ) {
                continue
            }

            val compressed = "$resource.${compressedType.extension}"
            val resolved = call.application.resolveResource(compressed) { url ->
                val requestPath = url.path.replace(
                    Regex("${Regex.escapeReplacement(compressed.substringAfterLast('/'))}$"),
                    resource.substringAfterLast('/')
                )
                contentType(URL(url.protocol, url.host, url.port, requestPath))
            } ?: continue

            resolvedByType[compressedType] = resolved
            yield(PreCompressedFileStrategy.Candidate(compressedType) { resolved.second.contentLength ?: 0L })
        }
    }

    val selected = strategy.select(candidates) ?: return null
    val (url, content) = resolvedByType.getValue(selected.type)
    return CompressedResource(url, content, selected.type)
}

internal suspend fun ApplicationCall.respondStaticFile(
    requestedFile: File,
    compressedTypes: Array<CompressedFileType>,
    acceptedEncodings: List<AcceptEncoding>,
    contentType: (File) -> ContentType = { ContentType.defaultForFile(it) },
    cacheControl: (File) -> List<CacheControl> = { emptyList() },
    lastModified: (File) -> GMTDate? = { null },
    etag: ETagProvider = ETagProvider { null },
    modify: suspend (File, ApplicationCall) -> Unit = { _, _ -> },
    strategy: PreCompressedFileStrategy = PreCompressedFileStrategy.FirstMatch,
) {
    if (!requestedFile.isFile) {
        return
    }

    attributes.put(StaticFileLocationProperty, requestedFile.path)

    val responseContentType = contentType(requestedFile)

    response.addCacheControlHeader(cacheControl(requestedFile))

    val bestCompressionFit = bestCompressionFit(requestedFile, compressedTypes, acceptedEncodings, strategy)

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
    acceptEncoding: List<AcceptEncoding>,
    compressedTypes: Array<CompressedFileType>,
    contentType: (Path) -> ContentType = { ContentType.defaultForPath(it) },
    cacheControl: (Path) -> List<CacheControl> = { emptyList() },
    modify: suspend (Path, ApplicationCall) -> Unit = { _, _ -> },
    lastModified: (Path) -> GMTDate? = { null },
    etag: ETagProvider = ETagProvider { null },
    strategy: PreCompressedFileStrategy = PreCompressedFileStrategy.FirstMatch,
) {
    if (!requestedPath.exists()) {
        return
    }

    attributes.put(StaticFileLocationProperty, requestedPath.toString())

    val responseContentType = contentType(requestedPath)

    response.addCacheControlHeader(cacheControl(requestedPath))

    val bestCompressionFit =
        bestCompressionFit(fileSystem, requestedPath, compressedTypes, acceptEncoding, strategy)

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

internal suspend fun <T : Any> ApplicationCall.respondCachedStaticFile(
    requestedPath: T,
    cachedFile: CachedStaticFile<T>,
    cachedCompressedFiles: Array<Pair<CachedStaticFile<T>, CompressedFileType>>,
    acceptedEncodings: List<AcceptEncoding>,
    modify: suspend (T, ApplicationCall) -> Unit = { _, _ -> },
    strategy: PreCompressedFileStrategy = PreCompressedFileStrategy.FirstMatch,
) {
    attributes.put(StaticFileLocationProperty, requestedPath.toString())

    response.addCacheControlHeader(cachedFile.cacheControl)

    val bestCompressionFit = bestCompressionFit(cachedCompressedFiles, acceptedEncodings, strategy)

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
    normalizedResourcePath: String,
    compressedTypes: Array<CompressedFileType>,
    acceptedEncodings: List<AcceptEncoding>,
    contentType: (URL) -> ContentType = { ContentType.defaultForFileExtension(it.path.extension()) },
    cacheControl: (URL) -> List<CacheControl> = { emptyList() },
    modifier: suspend (URL, ApplicationCall) -> Unit = { _, _ -> },
    lastModified: (URL) -> GMTDate? = { null },
    etag: ETagProvider = ETagProvider { null },
    strategy: PreCompressedFileStrategy = PreCompressedFileStrategy.FirstMatch,
) {
    attributes.put(StaticFileLocationProperty, normalizedResourcePath)

    val bestCompressionFit = bestCompressionFit(
        call = this,
        resource = normalizedResourcePath,
        compressedTypes = compressedTypes,
        acceptedEncodings = acceptedEncodings,
        contentType = contentType,
        strategy = strategy,
    )

    if (bestCompressionFit == null) {
        val content = application.resolveResource(
            path = normalizedResourcePath,
            mimeResolve = contentType
        )

        if (content != null) {
            response.addCacheControlHeader(cacheControl(content.first))

            modifier(content.first, this)

            val outgoingContent = content.second.provideVersions(etag, lastModified, content.first)
            respond(outgoingContent)
        }
    } else {
        suppressCompression()

        response.addCacheControlHeader(cacheControl(bestCompressionFit.url))

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
    if (etag == null && lastModified == null) return this

    val newVersions = versions.toMutableList()
    if (etag != null) newVersions.add(etag)
    if (lastModified != null) newVersions.add(LastModifiedVersion(lastModified))
    versions = newVersions
    return this
}

private fun ApplicationResponse.addCacheControlHeader(cacheControlValues: List<CacheControl>) {
    if (cacheControlValues.isNotEmpty()) {
        header(HttpHeaders.CacheControl, cacheControlValues.joinToString(", "))
    }
}
