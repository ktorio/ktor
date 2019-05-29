/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import kotlin.test.*

class NonceSmokeTest {
    @Test
    fun test() {
        val nonceSet = HashSet<String>(4096)

        repeat(4096) {
            nonceSet.add(generateNonce())
        }

        assertTrue { nonceSet.size == 4096 }
    }
}
