/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.server.application.Application
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

internal actual fun Application.readFileContents(path: String): String? {
    val filePath = Path(path)
    return if (!SystemFileSystem.exists(filePath)) {
        null
    } else {
        SystemFileSystem.source(filePath).use { source ->
            source.buffered().readString()
        }
    }
}
