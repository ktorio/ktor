/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.dispatcher.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

internal fun testSockets(block: suspend CoroutineScope.(SocketDispatcher) -> Unit) {
    if (!PlatformUtils.IS_JVM && !PlatformUtils.IS_NATIVE) return
    testSuspend {
        withTimeout(1000) {
            SocketDispatcher().use { selector ->
                block(selector)
            }
        }
    }
}

internal expect fun Any.supportsUnixDomainSockets(): Boolean

internal expect fun createTempFilePath(basename: String): String
internal expect fun removeFile(path: String)
