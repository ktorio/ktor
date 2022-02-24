/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.internal.*
import kotlinx.coroutines.*
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

/**
 * Print exception stacktrace.
 */
public actual fun Throwable.printStack() {
    printStackTrace()
}

/**
 * This file is a copy of the [kotlinx.coroutines.internal.ExceptionConstructor] with a single difference:
 * [tryCopyException] takes additional argument with cause to use in recovered exception.
 */

private val throwableFields = Throwable::class.java.fieldsCountOrDefault(-1)
private val cacheLock = ReentrantReadWriteLock()
private typealias Ctor = (Throwable) -> Throwable?

// Replace it with ClassValue when Java 6 support is over
private val exceptionCtors: WeakHashMap<Class<out Throwable>, Ctor> = WeakHashMap()

/**
 * Try copy [exception] using [cause] as cause.
 */
@DangerousInternalIoApi
@OptIn(ExperimentalCoroutinesApi::class)
public fun <E : Throwable> tryCopyException(exception: E, cause: Throwable): E? {
    // Fast path for CopyableThrowable
    if (exception is CopyableThrowable<*>) {
        return runCatching { exception.createCopy() as E? }.getOrNull()
    }
    // Use cached ctor if found
    cacheLock.read { exceptionCtors[exception.javaClass] }?.let { cachedCtor ->
        return cachedCtor(exception) as E?
    }
    /*
     * Skip reflective copy if an exception has additional fields (that are usually populated in user-defined constructors)
     */
    if (throwableFields != exception.javaClass.fieldsCountOrDefault(0)) {
        cacheLock.write { exceptionCtors[exception.javaClass] = { null } }
        return null
    }
    /*
     * Try to reflectively find constructor(), constructor(message, cause), constructor(cause) or constructor(message).
     * Exceptions are shared among coroutines, so we should copy exception before recovering current stacktrace.
     */
    var ctor: Ctor? = null
    val constructors = exception.javaClass.constructors.sortedByDescending { it.parameterTypes.size }
    for (constructor in constructors) {
        ctor = createConstructor(constructor)
        if (ctor != null) break
    }
    // Store the resulting ctor to cache
    cacheLock.write { exceptionCtors[exception.javaClass] = ctor ?: { null } }
    return ctor?.invoke(cause) as E?
}

private fun createConstructor(constructor: Constructor<*>): Ctor? {
    val p = constructor.parameterTypes
    return when (p.size) {
        2 -> when {
            p[0] == String::class.java && p[1] == Throwable::class.java ->
                safeCtor { e -> constructor.newInstance(e.message, e) as Throwable }
            else -> null
        }
        1 -> when (p[0]) {
            Throwable::class.java ->
                safeCtor { e -> constructor.newInstance(e) as Throwable }
            String::class.java ->
                safeCtor { e -> (constructor.newInstance(e.message) as Throwable).also { it.initCause(e) } }
            else -> null
        }
        0 -> safeCtor { e -> (constructor.newInstance() as Throwable).also { it.initCause(e) } }
        else -> null
    }
}

private inline fun safeCtor(crossinline block: (Throwable) -> Throwable): Ctor =
    { e -> runCatching { block(e) }.getOrNull() }

private fun Class<*>.fieldsCountOrDefault(defaultValue: Int) =
    kotlin.runCatching { fieldsCount() }.getOrDefault(defaultValue)

private tailrec fun Class<*>.fieldsCount(accumulator: Int = 0): Int {
    val fieldsCount = declaredFields.count { !Modifier.isStatic(it.modifiers) }
    val totalFields = accumulator + fieldsCount
    val superClass = superclass ?: return totalFields
    return superClass.fieldsCount(totalFields)
}
