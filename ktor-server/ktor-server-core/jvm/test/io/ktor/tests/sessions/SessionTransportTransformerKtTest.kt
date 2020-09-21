/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.sessions.SessionTransportTransformer
import io.ktor.sessions.transformRead
import io.ktor.sessions.transformWrite
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTransportTransformerKtTest {
    @Test
    fun allTransformersShouldBeAppliedOnWrite() {
        assertEquals("0;1;2", listOf(Appending("1"), Appending("2")).transformWrite("0"))
    }

    @Test
    fun allTransformersShouldBeAppliedOnReadInReverseOrder() {
        assertEquals("0", listOf(Appending("1"), Appending("2")).transformRead("0;1;2"))
    }

    private class Appending(val value: String) : SessionTransportTransformer {
        companion object {
            public const val separator = ';'
        }

        override fun transformRead(transportValue: String): String? {
            return transportValue
                .substringBeforeLast(separator)
                .takeIf { transportValue.substringAfterLast(separator) == value }
        }

        override fun transformWrite(transportValue: String): String {
            return "$transportValue$separator$value"
        }
    }
}
