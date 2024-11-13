package io.ktor.http.cio

import io.ktor.utils.io.*
import kotlinx.coroutines.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

internal actual fun ByteReadChannel.discardBlocking() {
    runBlocking {
        discard()
    }
}
