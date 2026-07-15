/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class StringsToByteArrayJvmTest {

    @Test
    fun `unpaired surrogates are replaced with question mark on jvm`() {
        // Pins the String.getBytes replacement convention so a change in the
        // underlying implementation is caught deliberately.
        assertContentEquals(
            "abc?def".toByteArray(Charsets.UTF_8),
            "abc\ud800def".toByteArray(Charsets.UTF_8),
        )
        assertContentEquals(
            "tail?".toByteArray(Charsets.UTF_8),
            "tail\ude80".toByteArray(Charsets.UTF_8),
        )
    }
}
