/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.server.application.Application
import java.io.File

internal actual fun Application.readFileContents(path: String): String? {
    val resource = environment.classLoader.getResourceAsStream(path)?.use { input ->
        input.bufferedReader().readText()
    }

    if (resource != null) return resource

    val file = File(path)
    if (!file.exists()) {
        return null
    }

    return file.readText()
}
