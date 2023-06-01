/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlin.test.*

/**
 * Check that [block] completed with given type of root cause.
 */
public actual inline fun <reified T : Throwable> assertFailsAndContainsCause(block: () -> Unit) {
    var cause = assertFails(block)

    while (true) {
        if (cause is T) return
        cause = cause.cause ?: break
    }

    assertTrue("Expected root cause is ${T::class}, but got ${cause::class}") { cause is T }
}

/**
 * Asserts that a [block] fails with a specific exception of type [T] being thrown.
 */
public actual inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    kotlin.test.assertFailsWith<T> { block() }
}
