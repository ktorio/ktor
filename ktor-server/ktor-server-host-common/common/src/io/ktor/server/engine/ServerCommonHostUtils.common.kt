/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.coroutines.*

internal expect fun availableProcessors(): Int

internal expect fun exitProcess(status: Int): Nothing

internal expect val defaultWatchPaths: List<String>

internal expect val ioDispatcher: CoroutineDispatcher
