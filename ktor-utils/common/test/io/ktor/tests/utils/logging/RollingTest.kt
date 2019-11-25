/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import kotlin.test.*

class RollingTest {
    private val date = GMTDate(514658186158)
    private val fileSystem = TestFileSystem()
    private lateinit var view: CachedFilesView
    private lateinit var pattern: FilePathPattern

    @Test
    fun empty(): Unit = test("file-%i") {
        rolling()
        expectedList("file-1")
    }

    @Test
    fun singleFileAtStart(): Unit = test("file-%i") {
        havingFiles("file-1")
        rolling()
        expectedList("file-1", "file-2")
    }

    @Test
    fun singleFileInTheMiddle(): Unit = test("file-%i") {
        havingFiles("file-5")
        rolling()
        expectedList("file-1", "file-6")
    }

    @Test
    fun subDir(): Unit = test("dir/file-%i.log") {
        havingFiles("file-.log", "dir/file-.log", "dir/file-1.log")
        rolling()
        expectedList("dir/file-.log", "dir/file-1.log", "dir/file-2.log", "file-.log")
    }

    @Test
    fun subDirWithPattern(): Unit = test("%i/file.log") {
        havingFiles("some-file.log", "1/file.log")
        rolling()
        expectedList("1/file.log", "2/file.log", "some-file.log")
    }

    @Test
    fun datesInPattern(): Unit = test("file-%d{dd}-%i.log") {
        assertEquals("23", date.format(StringPatternDateFormat("dd")))
        havingFiles("file-xx-1.log", "file-21-1.log", "file-23-1.log", "file-23-2.log")
        rolling()
        expectedList("file-21-1.log", "file-23-1.log", "file-23-2.log", "file-23-3.log", "file-xx-1.log")
    }

    private fun test(pattern: String, block: () -> Unit) {
        this.pattern = FilePathPattern(pattern)
        this.view = CachedFilesView(fileSystem, this.pattern, null)
        fileSystem.addListener(view)
        view.list()
        fileSystem.addFile("log.log")

        try {
            block()
        } finally {
            fileSystem.removeListener(view)
            view.invalidate()
        }
    }

    private fun havingFiles(vararg paths: String) {
        paths.forEach {
            fileSystem.addFile(it, ByteArray(2))
        }
    }

    private fun rolling() {
        rollFiles(fileSystem, "log.log", view, pattern, date)
    }

    private fun expectedList(vararg expectedPaths: String) {
        val actualPaths = fileSystem.allFiles.filterIsInstance<TestFileSystem.Entry.File>().map { it.path }
        assertEquals(expectedPaths.toList().sorted(), actualPaths.sorted())
    }
}
