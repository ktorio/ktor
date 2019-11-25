/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.utils.io.core.*
import kotlin.test.*

class TestFileSystemTest {
    private val fileSystem = TestFileSystem()

    @Test
    fun smokeTest() {
        fileSystem.open("test.txt").use { out ->
            out.writeText("Hello")
        }
        fileSystem.assertFileContent("test.txt", "Hello")
    }
}
