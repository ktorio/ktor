/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.*
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext

@Suppress("UnusedReceiverParameter")
@OptIn(DelicateCoroutinesApi::class)
@Deprecated(
    "Use newFixedThreadPoolContext instead",
    ReplaceWith("newFixedThreadPoolContext(threads, name)", "kotlinx.coroutines.newFixedThreadPoolContext")
)
@InternalAPI
public fun Dispatchers.createFixedThreadDispatcher(name: String, threads: Int): CloseableCoroutineDispatcher =
    newFixedThreadPoolContext(threads, name)
