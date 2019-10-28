/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.util.*

/**
 * Check that [block] completed with given type of root cause.
 */
@InternalAPI
actual inline fun <reified T : Throwable> assertFailsWithRootCause(block: () -> Unit) {
    try {
        block()
        error("Expected ${T::class} exception, but it wasn't thrown")
    } catch (cause: Throwable) {
        if (cause !is T && cause.message?.contains(T::class.simpleName!!) == true) {
            error("Expected ${T::class} exception, but $cause was thrown instead")
        }
    }
}
