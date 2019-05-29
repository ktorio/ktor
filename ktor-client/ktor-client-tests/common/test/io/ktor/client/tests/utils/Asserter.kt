/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlin.test.*

fun assertArrayEquals(message: String, expected: ByteArray, actual: ByteArray) {
    assertTrue(message) { expected.contentEquals(actual) }
}
