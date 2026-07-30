/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.defaults

import io.ktor.client.engine.cio.*
import kotlin.test.Test
import kotlin.test.assertSame

class DefaultEngineAndroidNativeTest {

    @Test
    fun `android native uses the CIO engine`() {
        assertSame(CIO, DefaultEngine)
    }
}
