/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.content.*
import io.ktor.util.logging.*
import kotlinx.io.IOException
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.inputStream
import kotlin.use

private val LOGGER = KtorSimpleLogger("io.ktor.server.http.content.ETagProvider")

/**
 * Built‑in strategies for generating [io.ktor.http.HttpHeaders.ETag] for static content.
 *
 * Note: for this functionality to work, you need to install the [ConditionalHeaders] plugin.
 *
 * Usage:
 * ```
 * install(ConditionalHeaders)
 *
 * routing {
 *   staticFiles("/assets", File("assets")) {
 *     etag(ETagProvider.StrongSha256)
 *   }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.ETagProvider)
 */
public fun interface ETagProvider {

    /**
     * Provides an [EntityTagVersion] for the given resource or `null` if it cannot be provided.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.ETagProvider.provide)
     */
    public fun provide(resource: Any): EntityTagVersion?

    public companion object {

        private val etagCache by lazy { ConcurrentHashMap<String, EntityTagVersion>() }

        /**
         * Strong ETag provider based on SHA‑256 of the actual bytes of the resource.
         * On I/O failures no ETag is produced
         * Note: for this functionality to work, you need to install the [ConditionalHeaders] plugin.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.ETagProvider.Companion.StrongSha256)
         */
        public val StrongSha256: ETagProvider = ETagProvider { resource ->
            val (key, streamSupplier) = try {
                keyAndStreamSupplier(resource) ?: run {
                    LOGGER.warn("StrongSha256 ETag not supported for ${resource::class}")
                    return@ETagProvider null
                }
            } catch (cause: IOException) {
                // failed to read metadata: no ETag
                LOGGER.warn("Failed to prepare ETag computation for ${resource::class}: ${cause.message}")
                return@ETagProvider null
            }

            etagCache[key] ?: run {
                val computed = try {
                    streamSupplier().use { ins ->
                        val hex = sha256Hex(ins)
                        EntityTagVersion(hex, weak = false)
                    }
                } catch (cause: IOException) {
                    LOGGER.warn("Failed to compute ETag for resource $key: ${cause.message}")
                    null // no ETag
                }

                if (computed != null) {
                    etagCache.putIfAbsent(key, computed) ?: computed
                } else {
                    null
                }
            }
        }
    }
}

private fun keyAndStreamSupplier(resource: Any): Pair<String, () -> InputStream>? = when (resource) {
    is File -> {
        "${resource.absolutePath}:${resource.lastModified()}:${resource.length()}" to {
            resource.inputStream()
        }
    }

    is Path -> {
        val path = resource.toAbsolutePath()
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        "$path:${attrs.lastModifiedTime().toMillis()}:${attrs.size()}" to {
            Files.newInputStream(path)
        }
    }

    is URL -> {
        val meta = runCatching {
            val conn = resource.openConnection()
            (conn.lastModified.takeIf { it > 0 }?.toString() ?: "") +
                ":" + (conn.contentLengthLong.takeIf { it >= 0 }?.toString() ?: "")
        }.getOrDefault("")
        "${resource.toExternalForm()}:$meta" to { resource.openStream() }
    }

    else -> null
}

private fun sha256Hex(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(8192)
    var n: Int
    while (true) {
        n = input.read(buf)
        if (n == -1) break
        md.update(buf, 0, n)
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
