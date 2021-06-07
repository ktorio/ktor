/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.concurrent

import kotlinx.cinterop.*

internal class ThreadLocalValue<T : Any>(value: T) {
    private val reference = StableRef.create(value).asCPointer()
    private val createdIn = ThreadId.current

    val value: T?
        get() {
            if (createdIn == ThreadId.current) {
                @Suppress("UNCHECKED_CAST")
                return reference.asStableRef<Any>().get() as T
            }

            return null
        }
}
