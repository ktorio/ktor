/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Creates the selector manager for current platform.
 */
@Suppress("FunctionName")
public actual fun SelectorManager(dispatcher: kotlin.coroutines.CoroutineContext): SelectorManager {
    TODO("SelectorManager is not yet implemented on Mingw x64 platform")
}

public actual interface SelectorManager : CoroutineScope, Closeable {

}
