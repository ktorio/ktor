/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFilePath
import io.ktor.server.application.call
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
 * Serves files from [localPath] under [remotePath].
 *
 * @param remotePath the route path at which files are served.
 * @param localPath the local directory containing files to serve.
 * @return the route serving the files.
 */
@ExperimentalKtorApi
public fun Route.staticFiles(remotePath: String, localPath: Path, fileSystem: FileSystem = SystemFileSystem): Route =
    route(remotePath) {
        get("{$staticFilesPathParameter...}") {
            val segments = call.parameters.getAll(staticFilesPathParameter)
                ?.normalizePathComponents()
                ?.takeIf { it.isNotEmpty() } ?: return@get
            val path = Path(localPath, *segments.toTypedArray())
            val channel = try {
                fileSystem.byteReadChannel(path).also { channel ->
                    try {
                        channel.awaitContent(1)
                    }  catch (cause: IOException) {
                        channel.cancel(cause)
                        return@get
                    }
                }
            } catch (_: IOException) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(object : OutgoingContent.ReadChannelContent() {
                override val contentType: ContentType = ContentType.defaultForFilePath(segments.last())
                override fun readFrom(): ByteReadChannel = channel
            })
        }
    }

private const val staticFilesPathParameter = "static-files-path"
