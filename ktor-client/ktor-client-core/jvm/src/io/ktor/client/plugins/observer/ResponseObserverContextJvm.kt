/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.observer

import kotlinx.coroutines.slf4j.*
import kotlin.coroutines.*
import kotlinx.coroutines.currentCoroutineContext

internal actual suspend fun getResponseObserverContext(): CoroutineContext =
    currentCoroutineContext()[MDCContext] ?: EmptyCoroutineContext
