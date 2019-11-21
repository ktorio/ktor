/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.logging.rolling.*
import kotlin.test.*

class FileSystemListingTest {
    private val fileSystem = TestFileSystem()

    @Test
    fun testEmpty() {
        assertEquals(emptyList(), fileSystem.list(FilePathPattern("pattern-%i")).toList())
        assertEquals(emptyList(), fileSystem.list(FilePathPattern("dir/pattern-%i")).toList())
        assertEquals(emptyList(), fileSystem.list(FilePathPattern("dir-%i/pattern-%i")).toList())
    }

    @Test
    fun testSingle() {
        fileSystem.addFile("path-1")
        fileSystem.addFile("long-long-log-file-1")
        fileSystem.addFile("long-long-log-file-2")
        fileSystem.addFile("log-file-1")
        fileSystem.addFile("log-file-20")
        fileSystem.addFile("sub/log-file-1")

        assertEquals(
            listOf("log-file-1", "log-file-20"),
            fileSystem.list(FilePathPattern("log-file-%i")).toList()
        )
    }

    @Test
    fun testTwoComponentPath() {
        fileSystem.addFile("log-file-1")
        fileSystem.addFile("dir/log-file-1")
        fileSystem.addFile("dir/dir/log-file-1")

        assertEquals(
            listOf("dir/log-file-1"),
            fileSystem.list(FilePathPattern("dir/log-file-%i")).toList()
        )
    }

    @Test
    fun testDirectoryPattern() {
        fileSystem.addFile("1/99")
        fileSystem.addFile("2/log-file")
        fileSystem.addFile("z")

        assertEquals(
            listOf("2/log-file"),
            fileSystem.list(FilePathPattern("%i/log-file")).toList()
        )

        assertEquals(
            listOf("1/99"),
            fileSystem.list(FilePathPattern("%i/%i")).toList()
        )
    }
}
