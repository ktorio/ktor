// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

internal actual class SharedSet<T> actual constructor() {
    private val delegate = mutableSetOf<T>()

    actual val size: Int get() = delegate.size

    actual fun add(element: T): Boolean = delegate.add(element)

    actual fun remove(element: T): Boolean = delegate.remove(element)
}
