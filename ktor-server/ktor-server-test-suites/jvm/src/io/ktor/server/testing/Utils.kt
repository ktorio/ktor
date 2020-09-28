/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import java.io.*
import java.util.zip.*
import kotlin.test.*

internal suspend fun assertFailsSuspend(block: suspend () -> Unit): Throwable {
    var exception: Throwable? = null
    try {
        block()
    } catch (cause: Throwable) {
        exception = cause
    }

    assertNotNull(exception)
    return exception
}

internal fun InputStream.crcWithSize(): Pair<Long, Long> {
    val checksum = CRC32()
    val bytes = ByteArray(8192)
    var count = 0L

    do {
        val rc = read(bytes)
        if (rc == -1) {
            break
        }
        count += rc
        checksum.update(bytes, 0, rc)
    } while (true)

    return checksum.value to count
}

internal fun String.urlPath() = replace("\\", "/")

internal class ExpectedException(message: String) : RuntimeException(message)

internal fun loadTestFile(): File = listOf(
    File("jvm/src"),
    File("jvm/test"),
    File("ktor-server/ktor-server-core/jvm/src")
).filter { it.exists() }
    .flatMap { it.walkBottomUp().asIterable() }
    .first { it.extension == "kt" }
