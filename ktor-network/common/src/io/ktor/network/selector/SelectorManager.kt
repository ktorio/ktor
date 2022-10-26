package io.ktor.network.selector

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Creates the selector manager for current platform.
 */
@Suppress("FunctionName")
public expect fun SelectorManager(
    dispatcher: CoroutineContext = EmptyCoroutineContext
): SelectorManager

public expect interface SelectorManager : CoroutineScope, Closeable
