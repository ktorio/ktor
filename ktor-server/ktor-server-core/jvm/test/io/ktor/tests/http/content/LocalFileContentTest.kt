/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.content

import io.ktor.server.http.content.*
import java.io.*
import kotlin.test.*

class LocalFileContentTest {

    @Test
    fun testCreateWithFile() {
        val file = File.createTempFile("test", "txt")
        LocalFileContent(file)
    }

    @Test
    fun testCreateWithFileZeroLastModifiedDate() {
        val file = File.createTempFile("test-zero-date", "txt")
        file.setLastModified(0)
        LocalFileContent(file)
    }

    @Test
    fun testCreateWithNonExistingFile() {
        val file = File("test-doesnt-exist", "txt")
        assertFailsWith<IOException> {
            LocalFileContent(file)
        }
    }
}
