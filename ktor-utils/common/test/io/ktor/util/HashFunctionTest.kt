/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

class HashFunctionTest {
    @Test
    fun sha1() {
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            hex(Sha1().digest("abc".encodeToByteArray()))
        )
    }
}
