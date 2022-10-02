/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*

actual val Dispatchers.IOBridge: CoroutineDispatcher get() = Default

actual val testResourcesFolder: String
    get() {
        val root = memScoped {
            val result = allocArray<ByteVar>(512)
            getcwd(result, 512)
            result.toKString()
        }
        return "$root/jvmAndNix/test-resources"
    }
