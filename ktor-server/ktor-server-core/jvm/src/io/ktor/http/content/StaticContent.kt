/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import java.io.*

private const val pathParameterName = "static-content-path-parameter"

private val staticRootFolderKey = AttributeKey<File>("BaseFolder")

private val compressedKey = AttributeKey<List<CompressedFileType>>("StaticContentCompressed")

/**
 * Supported pre compressed file types and associated extensions
 *
 * **See Also:** [Accept-Encoding](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding)
 */
public enum class CompressedFileType(public val extension: String, public val encoding: String = extension) {
    // https://www.theregister.co.uk/2015/10/11/googles_bro_file_format_changed_to_br_after_gender_politics_worries/
    BROTLI("br"),
    GZIP("gz", "gzip");

    public fun file(plain: File): File = File("${plain.absolutePath}.$extension")
}

/**
 * Support pre-compressed files in the file system only (not just any classpath resource)
 *
 * For example, by using [preCompressed()] (or [preCompressed(CompressedFileType.BROTLI)]), the local file
 * /foo/bar.js.br can be found @ http..../foo/bar.js
 *
 * Appropriate headers will be set and compression will be suppressed if pre-compressed file is found
 *
 * Notes:
 *
 * * The order in types is *important*, it will determine the priority of serving one versus serving another
 *
 * * This can't be disabled in a child route if it was enabled in the root route
 */
public fun Route.preCompressed(
    vararg types: CompressedFileType = CompressedFileType.values(),
    configure: Route.() -> Unit
) {
    val existing = staticContentEncodedTypes ?: emptyList()
    val mixedTypes = (existing + types.asList()).distinct()
    attributes.put(compressedKey, mixedTypes)
    configure()
    attributes.remove(compressedKey)
}

private val Route.staticContentEncodedTypes: List<CompressedFileType>?
    get() = attributes.getOrNull(compressedKey) ?: parent?.staticContentEncodedTypes

/**
 * Base folder for relative files calculations for static content
 */
public var Route.staticRootFolder: File?
    get() = attributes.getOrNull(staticRootFolderKey) ?: parent?.staticRootFolder
    set(value) {
        if (value != null) {
            attributes.put(staticRootFolderKey, value)
        } else {
            attributes.remove(staticRootFolderKey)
        }
    }

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

/**
 * Create a block for static content
 */
public fun Route.static(configure: Route.() -> Unit): Route = apply(configure)

/**
 * Create a block for static content at specified [remotePath]
 */
public fun Route.static(remotePath: String, configure: Route.() -> Unit): Route = route(remotePath, configure)

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
public fun Route.default(localPath: String): Unit = default(File(localPath))

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
public fun Route.default(localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes
    get {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
public fun Route.file(remotePath: String, localPath: String = remotePath): Unit = file(remotePath, File(localPath))

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
public fun Route.file(remotePath: String, localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes
    get(remotePath) {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve all files from [folder]
 */
public fun Route.files(folder: String): Unit = files(File(folder))

/**
 * Sets up routing to serve all files from [folder]
 */
public fun Route.files(folder: File) {
    val dir = staticRootFolder.combine(folder)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = dir.combineSafe(relativePath)
        call.respondStaticFile(file, compressedTypes)
    }
}

private suspend inline fun ApplicationCall.respondStaticFile(
    requestedFile: File,
    compressedTypes: List<CompressedFileType>?
) {
    val bestCompressionFit = requestedFile.bestCompressionFit(request.acceptEncodingItems(), compressedTypes)
    bestCompressionFit?.run {
        attributes.put(Compression.SuppressionAttribute, true)
    }
    val localFile = bestCompressionFit?.file(requestedFile) ?: requestedFile
    if (localFile.isFile) {
        val localFileContent = LocalFileContent(localFile, ContentType.defaultForFile(requestedFile))
        respond(PreCompressedResponse(localFileContent, bestCompressionFit?.encoding))
    }
}

private class PreCompressedResponse(
    val original: ReadChannelContent,
    val encoding: String?,
) : OutgoingContent.ReadChannelContent() {
    override val contentLength get() = original.contentLength
    override val contentType get() = original.contentType
    override val status get() = original.status
    override fun readFrom() = original.readFrom()
    override fun readFrom(range: LongRange) = original.readFrom(range)
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        if (encoding != null) {
            Headers.build {
                appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                append(HttpHeaders.ContentEncoding, encoding)
            }
        } else original.headers
    }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
}

private fun File.bestCompressionFit(
    acceptEncoding: List<HeaderValue>,
    compressedTypes: List<CompressedFileType>?
): CompressedFileType? {
    val acceptedEncodings = acceptEncoding.map { it.value }.toSet()
    // We respect the order in compressedTypes, not the one on Accept header
    return compressedTypes?.filter {
        it.encoding in acceptedEncodings
    }?.firstOrNull { it.file(this).isFile }
}

private val staticBasePackageName = AttributeKey<String>("BasePackage")

/**
 * Base package for relative resources calculations for static content
 */
public var Route.staticBasePackage: String?
    get() = attributes.getOrNull(staticBasePackageName) ?: parent?.staticBasePackage
    set(value) {
        if (value != null) {
            attributes.put(staticBasePackageName, value)
        } else {
            attributes.remove(staticBasePackageName)
        }
    }

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

/**
 * Sets up routing to serve [resource] as [remotePath] in [resourcePackage]
 */
public fun Route.resource(remotePath: String, resource: String = remotePath, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get(remotePath) {
        val content = call.resolveResource(resource, packageName)
        if (content != null) {
            call.respond(content)
        }
    }
}

/**
 * Sets up routing to serve all resources in [resourcePackage]
 */
public fun Route.resources(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val content = call.resolveResource(relativePath, packageName)
        if (content != null) {
            call.respond(content)
        }
    }
}

/**
 * Specifies [resource] as a default resources to serve when folder is requested
 */
public fun Route.defaultResource(resource: String, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get {
        val content = call.resolveResource(resource, packageName)
        if (content != null) {
            call.respond(content)
        }
    }
}
