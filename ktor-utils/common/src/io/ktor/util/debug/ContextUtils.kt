/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.debug

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Adds [elements] to the current [CoroutineContext] if Intellij JVM debugger is attached.
 * Similar to [withContext] but will only add [elements] to coroutine context in IDE debug mode.
 * */
public suspend fun <T> addToContextInDebugMode(
    vararg elements: AbstractCoroutineContextElement,
    block: suspend () -> T
): T {
    if (!IntellijIdeaDebugDetector.isDebuggerConnected) return block()

    val debugContext = elements.fold(currentCoroutineContext()) { context, element -> context + element }
    return withContext(debugContext) { block() }
}

/**
 * Performs [action] on the current element of the [CoroutineContext] with the given [key] if Intellij JVM debugger is
 * attached.
 * */
public suspend fun <Element : CoroutineContext.Element> useContextElementInDebugMode(
    key: CoroutineContext.Key<Element>,
    action: (Element) -> Unit
) {
    if (!IntellijIdeaDebugDetector.isDebuggerConnected) return

    currentCoroutineContext()[key]?.let(action)
}
