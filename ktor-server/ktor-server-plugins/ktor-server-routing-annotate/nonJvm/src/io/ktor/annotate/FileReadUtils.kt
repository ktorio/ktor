/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.server.application.Application
import kotlinx.io.buffered
import kotlinx.io.readString

internal actual fun Application.readFileContents(path: String): String? {
    val filePath = kotlinx.io.files.Path(path)
    return if (!kotlinx.io.files.SystemFileSystem.exists(filePath)) {
        null
    } else {
        kotlinx.io.files.SystemFileSystem.source(filePath).use { source ->
            source.buffered().readString()
        }
    }
}
