/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.util.*
import kotlin.test.*

/**
 * Check that [block] completed with given type of root cause.
 */
@InternalAPI
actual inline fun <reified T : Throwable> assertFailsWithRootCause(block: () -> Unit) {
    var cause = assertFails(block)
    while (cause.cause != null) {
        cause = cause.cause!!
    }

    assertTrue("Expected root cause is ${T::class}, but got ${cause::class}") { cause is T }
}
