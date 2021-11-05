/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.posix.*

internal actual val WORKING_DIRECTORY_PATH: String get() = memScoped {
    val result = allocArray<ByteVar>(512)
    getcwd(result, 512)
    result.toKString()
}
