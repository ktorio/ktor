/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlinx.coroutines.*
import java.io.*

actual val Dispatchers.IOBridge: CoroutineDispatcher get() = IO

actual val testResourcesFolder: String get() = File("jvmAndNix/test-resources").absolutePath
