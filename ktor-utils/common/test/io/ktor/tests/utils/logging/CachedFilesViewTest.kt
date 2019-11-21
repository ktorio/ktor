/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class CachedFilesViewTest {
    private val underlyingFileSystem = TestFileSystem()
    private val fs = CloseableFileSystem(underlyingFileSystem)

    @Test
    fun smokeTest() {
        val view = viewFor("log-file-%i")
        assertEquals(emptyList(), view.list())

        underlyingFileSystem.addFile("log-file")
        underlyingFileSystem.addFile("log-file-1")
        underlyingFileSystem.addFile("log-file-3")
        underlyingFileSystem.addFile("dir/log-file-3")

        fs.close()
        assertEquals(listOf("log-file-1", "log-file-3"), view.list().sorted())
    }

    @Test
    fun smokeTestDirPattern() {
        val view = viewFor("dir-%i/log-file-%i")
        assertEquals(emptyList(), view.list())

        underlyingFileSystem.addFile("log-file-3")
        underlyingFileSystem.addFile("dir/log-file-4")
        underlyingFileSystem.addFile("dir-4/log-file-4")
        underlyingFileSystem.addFile("dir-9/log-file-9")

        fs.close()
        assertEquals(listOf("dir-4/log-file-4", "dir-9/log-file-9"), view.list().sorted())

        underlyingFileSystem.delete("dir-4/log-file-4")
        assertEquals(listOf("dir-9/log-file-9"), view.list().sorted())
    }

    private fun viewFor(pattern: String): CachedFilesView = CachedFilesView(fs, FilePathPattern(pattern)).apply {
        fs.addListener(this)
    }

    private class CloseableFileSystem(val delegate: FileSystem) : FileSystem(), FileSystemListener {
        private var closed = false
        override val listeners = ArrayList<FileSystemListener>()

        init {
            delegate.addListener(this)
        }

        fun close() {
            closed = true
        }

        override fun openImpl(filePath: String): Output {
            check(!closed)
            return delegate.open(filePath)
        }

        override fun renameImpl(fromPath: String, toPath: String): Boolean {
            check(!closed)
            return delegate.rename(fromPath, toPath)
        }

        override fun deleteImpl(filePath: String) {
            check(!closed)
            return delegate.delete(filePath)
        }

        override fun list(directoryPath: String): List<String> {
            check(!closed)
            return delegate.list(directoryPath)
        }

        override fun size(file: String): Long {
            check(!closed)
            return delegate.size(file)
        }

        override fun lastModified(name: String): GMTDate {
            check(!closed)
            return delegate.lastModified(name)
        }

        override fun contains(path: String): Boolean {
            check(!closed)
            return delegate.contains(path)
        }

        override fun fileCreated(path: String) {
            listeners.forEach { it.fileCreated(path) }
        }

        override fun fileRemoved(path: String) {
            listeners.forEach { it.fileRemoved(path) }
        }

        override fun fileUpdated(path: String) {
            listeners.forEach { it.fileUpdated(path) }
        }
    }
}
