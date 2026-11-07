/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

internal object NativeLibraryLoader {
    private val isLoaded: Boolean by lazy(::loadLibrary)

    fun load(): Boolean = isLoaded

    private fun loadLibrary(): Boolean {
        val resourcePath = resourcePath() ?: return false
        return try {
            val resource = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath) ?: return false
            val libraryFile = Files.createTempFile("ktor-io-uring-", ".so")
            libraryFile.toFile().deleteOnExit()
            resource.use { input -> Files.copy(input, libraryFile, StandardCopyOption.REPLACE_EXISTING) }
            System.load(libraryFile.toAbsolutePath().toString())
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun resourcePath(): String? {
        val osName = System.getProperty("os.name") ?: return null
        if (!osName.startsWith("Linux", ignoreCase = true)) return null

        val architecture = when (System.getProperty("os.arch")?.lowercase(Locale.ROOT)) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> return null
        }
        return "/META-INF/native/linux-$architecture/libktor_io_uring.so"
    }
}
