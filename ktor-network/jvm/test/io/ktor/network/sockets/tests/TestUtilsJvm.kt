/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import java.nio.file.*
import kotlin.io.path.*

private const val UNIX_DOMAIN_SOCKET_ADDRESS_CLASS = "java.net.UnixDomainSocketAddress"

internal actual fun Any.supportsUnixDomainSockets(): Boolean {
    return try {
        Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS, false, javaClass.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}

internal actual fun createTempFilePath(basename: String): String {
    val tempFile = Files.createTempFile(basename, "")
    tempFile.deleteIfExists()
    return tempFile.toString()
}

internal actual fun removeFile(path: String) {
    Files.deleteIfExists(Path(path))
}
