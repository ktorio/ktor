/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.content.*
import kotlinx.io.files.Path
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.inputStream
import kotlin.use

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
 *     etag(EntityTagMode.StrongSha256)
 *   }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.EntityTagMode)
 */
public object EntityTagMode {

    private val etagCache by lazy { ConcurrentHashMap<String, EntityTagVersion>() }

    /**
     * Strong ETag provider based on SHA‑256 of the actual bytes of the resource.
     * Note: for this functionality to work, you need to install the [ConditionalHeaders] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.EntityTagMode.StrongSha256)
     */
    public val StrongSha256: (Any) -> EntityTagVersion? = { resource ->
        val (key, streamSupplier) = keyAndStreamSupplier(resource)
        etagCache.computeIfAbsent(key) {
            streamSupplier().use { ins ->
                val hex = sha256Hex(ins)
                EntityTagVersion(hex, weak = false)
            }
        }
    }

    private fun keyAndStreamSupplier(resource: Any): Pair<String, () -> InputStream> = when (resource) {
        is File -> {
            "S:F:${resource.absolutePath}:${resource.lastModified()}:${resource.length()}" to {
                resource.inputStream()
            }
        }

        is Path -> {
            val path = Paths.get(resource.toString())
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            "S:P:${path.toAbsolutePath()}:${attrs.lastModifiedTime().toMillis()}:${attrs.size()}" to {
                Files.newInputStream(path)
            }
        }

        is URL -> {
            "S:U:${resource.toExternalForm()}" to { resource.openStream() }
        }

        else -> throw IllegalArgumentException("Unsupported resource type: ${resource::class}")
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
}
