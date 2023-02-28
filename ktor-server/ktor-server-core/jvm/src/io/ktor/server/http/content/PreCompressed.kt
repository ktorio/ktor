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
import java.io.*

/**
 * Supported pre compressed file types and associated extensions
 *
 * **See Also:** [Accept-Encoding](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding)
 */
public enum class CompressedFileType(public val extension: String, public val encoding: String = extension) {
    // https://www.theregister.co.uk/2015/10/11/googles_bro_file_format_changed_to_br_after_gender_politics_worries/
    BROTLI("br"),
    GZIP("gz", "gzip");

    @Deprecated("This will become internal")
    public fun file(plain: File): File = File("${plain.absolutePath}.$extension")
}

internal val compressedKey = AttributeKey<List<CompressedFileType>>("StaticContentCompressed")

internal val Route.staticContentEncodedTypes: List<CompressedFileType>?
    get() = attributes.getOrNull(compressedKey) ?: parent?.staticContentEncodedTypes

internal class PreCompressedResponse(
    private val original: ReadChannelContent,
    private val encoding: String?,
) : OutgoingContent.ReadChannelContent() {
    override val contentLength get() = original.contentLength
    override val contentType get() = original.contentType
    override val status get() = original.status
    override fun readFrom() = original.readFrom()
    override fun readFrom(range: LongRange) = original.readFrom(range)
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        if (encoding == null) return@lazy original.headers

        Headers.build {
            appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, encoding)
        }
    }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
}

internal fun bestCompressionFit(
    file: File,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: List<CompressedFileType>?
): CompressedFileType? {
    val acceptedEncodings = acceptEncoding.map { it.value }.toSet()
    // We respect the order in compressedTypes, not the one on Accept header
    @Suppress("DEPRECATION")
    return compressedTypes
        ?.filter { it.encoding in acceptedEncodings }
        ?.firstOrNull { it.file(file).isFile }
}

internal fun bestCompressionFit(
    call: ApplicationCall,
    resource: String,
    packageName: String?,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: List<CompressedFileType>?
): CompressedFileType? {
    val acceptedEncodings = acceptEncoding.map { it.value }.toSet()
    // We respect the order in compressedTypes, not the one on Accept header
    return compressedTypes
        ?.filter { it.encoding in acceptedEncodings }
        ?.firstOrNull {
            val compressed = "$resource.${it.extension}"
            call.resolveResource(compressed, packageName) != null
        }
}

internal suspend inline fun ApplicationCall.respondStaticFile(
    requestedFile: File,
    compressedTypes: List<CompressedFileType>?
) {
    val bestCompressionFit = bestCompressionFit(requestedFile, request.acceptEncodingItems(), compressedTypes)
    if (bestCompressionFit == null) {
        if (requestedFile.isFile) {
            respond(LocalFileContent(requestedFile, ContentType.defaultForFile(requestedFile)))
        }
        return
    }
    attributes.put(SuppressionAttribute, true)
    @Suppress("DEPRECATION")
    val compressedFile = bestCompressionFit.file(requestedFile)
    if (compressedFile.isFile) {
        val localFileContent = LocalFileContent(compressedFile, ContentType.defaultForFile(requestedFile))
        respond(PreCompressedResponse(localFileContent, bestCompressionFit.encoding))
    }
}

internal suspend inline fun ApplicationCall.respondStaticResource(
    requestedResource: String,
    packageName: String?,
    compressedTypes: List<CompressedFileType>?
) {
    val bestCompressionFit = bestCompressionFit(
        call = this,
        resource = requestedResource,
        packageName = packageName,
        acceptEncoding = request.acceptEncodingItems(),
        compressedTypes = compressedTypes
    )

    if (bestCompressionFit == null) {
        val content = resolveResource(path = requestedResource, resourcePackage = packageName)
        if (content != null) {
            respond(content)
        }
        return
    }
    attributes.put(SuppressionAttribute, true)
    val compressedResource = resolveResource(
        path = "$requestedResource.${bestCompressionFit.extension}",
        resourcePackage = packageName
    ) {
        ContentType.defaultForFileExtension(requestedResource.extension())
    }
    if (compressedResource != null) {
        respond(PreCompressedResponse(compressedResource, bestCompressionFit.encoding))
    }
}
