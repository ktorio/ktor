/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFilePath
import io.ktor.server.request.acceptEncodingItems
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.normalizePathComponents
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.files.byteReadChannel
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Serves files from [localPath] under [remotePath], including available pre-compressed versions.
 *
 * @param remotePath the route path at which files are served.
 * @param localPath the local directory containing files to serve.
 * @return the route serving the files.
 */
@ExperimentalKtorApi
public fun Route.staticFiles(remotePath: String, localPath: Path): Route {
    val compressedEncodings = findCompressedEncodings(SystemFileSystem, localPath)

    return route(remotePath) {
        get("{$staticFilesPathParameter...}") {
            val segments = call.parameters.getAll(staticFilesPathParameter)
                ?.normalizePathComponents()
                ?.takeIf { it.isNotEmpty() } ?: return@get
            val path = Path(localPath, *segments.toTypedArray())

            val acceptedEncodings = if (compressedEncodings.isEmpty()) {
                emptyList()
            } else {
                call.request.acceptEncodingItems()
            }
            fun CompressedFileEncoding.isRefused() =
                acceptedEncodings.none { it.quality > 0.0 && it.value.equals(encoding, ignoreCase = true) }
            var selectedEncoding: CompressedFileEncoding? = null
            var channel: ByteReadChannel? = null
            for (encoding in compressedEncodings) {
                if (encoding.isRefused()) {
                    continue
                }

                val encodedPath = Path("$path.${encoding.extension}")
                channel = openFileChannel(SystemFileSystem, encodedPath)
                if (channel != null) {
                    selectedEncoding = encoding
                    break
                }
            }

            val responseChannel = channel ?: openFileChannel(SystemFileSystem, path) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (compressedEncodings.isNotEmpty()) {
                call.response.headers.append(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
            }
            selectedEncoding?.let { encoding ->
                call.response.headers.append(HttpHeaders.ContentEncoding, encoding.encoding)
                call.suppressCompression()
            }

            call.respond(object : OutgoingContent.ReadChannelContent() {
                override val contentType: ContentType = ContentType.defaultForFilePath(segments.last())
                override fun readFrom(): ByteReadChannel = responseChannel
            })
        }
    }
}

private data class CompressedFileEncoding(val extension: String, val encoding: String)

private data class CompressedFileSizes(var compressed: Long = 0L, var original: Long = 0L)

private val supportedCompressedEncodings = listOf(
    CompressedFileEncoding("zst", "zstd"),
    CompressedFileEncoding("br", "br"),
    CompressedFileEncoding("gz", "gzip"),
    CompressedFileEncoding("deflate", "deflate")
)

private fun findCompressedEncodings(fileSystem: FileSystem, localPath: Path): List<CompressedFileEncoding> {
    val sizes = mutableMapOf<CompressedFileEncoding, CompressedFileSizes>()

    fun scan(directory: Path) {
        val entries = try {
            fileSystem.list(directory)
        } catch (_: IOException) {
            return
        }

        for (entry in entries) {
            val metadata = try {
                fileSystem.metadataOrNull(entry)
            } catch (_: IOException) {
                null
            } ?: continue

            if (metadata.isDirectory) {
                scan(entry)
                continue
            }
            if (!metadata.isRegularFile) continue

            val entryName = entry.toString()
            for (encoding in supportedCompressedEncodings) {
                val suffix = ".${encoding.extension}"
                if (!entryName.endsWith(suffix)) continue

                val originalPath = Path(entryName.removeSuffix(suffix))
                val originalMetadata = try {
                    fileSystem.metadataOrNull(originalPath)
                } catch (_: IOException) {
                    null
                } ?: continue
                if (!originalMetadata.isRegularFile) continue

                val compressedSize = metadata.size ?: continue
                val originalSize = originalMetadata.size ?: continue
                val encodingSizes = sizes.getOrPut(encoding) { CompressedFileSizes() }
                encodingSizes.compressed += compressedSize
                encodingSizes.original += originalSize
            }
        }
    }

    scan(localPath)

    return supportedCompressedEncodings
        .filter { it in sizes }
        .sortedBy { encoding ->
            val encodingSizes = sizes.getValue(encoding)
            if (encodingSizes.original == 0L) {
                encodingSizes.compressed.toDouble()
            } else {
                encodingSizes.compressed.toDouble() / encodingSizes.original
            }
        }
}

@OptIn(ExperimentalKtorApi::class)
private suspend fun openFileChannel(fileSystem: FileSystem, path: Path): ByteReadChannel? {
    val channel = try {
        fileSystem.byteReadChannel(path)
    } catch (_: IOException) {
        return null
    }

    try {
        channel.awaitContent(1)
    } catch (cause: IOException) {
        channel.cancel(cause)
        return null
    }

    return channel
}

private const val staticFilesPathParameter = "static-files-path"
