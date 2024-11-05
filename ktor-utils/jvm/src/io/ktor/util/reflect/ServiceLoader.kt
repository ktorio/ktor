/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import io.ktor.utils.io.*
import java.util.*

/**
 * Loads implementations of service [T] using [ServiceLoader.load].
 *
 * NOTE: ServiceLoader should use specific call convention to be optimized by R8 on Android:
 * `ServiceLoader.load(X.class, X.class.getClassLoader()).iterator()`
 * See: https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
 */
@InternalAPI
public inline fun <reified T> loadService(): Iterable<T> = Iterable {
    ServiceLoader.load(
        T::class.java,
        T::class.java.classLoader,
    ).iterator()
}
