/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.defaultheaders

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun readVersion(): String {
    memScoped {
        val versionFilePath = "/nix/resources/ktor-version.txt"
        val versionFile: CPointer<FILE>? = fopen(versionFilePath, "r")

        if (versionFile != null) {
            try {
                val bufferLength = 128
                val buffer = allocArray<ByteVar>(bufferLength)

                return fgets(buffer, bufferLength, versionFile)?.toKString()?.removeSuffix("\n") ?: "debug"
            } finally {
                fclose(versionFile)
            }
        }

        return "debug"
    }
}
