/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core.internal

import kotlin.contracts.*

@PublishedApi
internal inline fun require(condition: Boolean, crossinline message: () -> String) {
    contract {
        returns() implies condition
    }

    if (!condition) {
        val m = object : RequireFailureCapture() {
            override fun doFail(): Nothing {
                throw IllegalArgumentException(message())
            }
        }
        m.doFail()
    }
}

@PublishedApi
internal abstract class RequireFailureCapture {
    abstract fun doFail(): Nothing
}
