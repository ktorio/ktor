/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlin.test.*

class MultipartJvmTest {

    @Suppress("DEPRECATION")
    @Test
    fun testStreamProvider() {
        val fileItem = PartData.FileItem({ ByteReadChannel(ByteArray(4097) { 1 }) }, {}, Headers.Empty)
        val stream = fileItem.streamProvider()

        val buffer = ByteArray(4097)
        val read = stream.read(buffer)
        assertEquals(4097, read)
        assertEquals(ByteArray(4097) { 1 }.toList(), buffer.toList())
    }
}
