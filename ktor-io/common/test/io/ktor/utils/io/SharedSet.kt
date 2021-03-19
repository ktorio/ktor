/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

internal expect class SharedSet<T>() {
    val size: Int

    fun add(element: T): Boolean
    fun remove(element: T): Boolean
}
