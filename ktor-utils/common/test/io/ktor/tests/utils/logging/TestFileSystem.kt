/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import kotlin.test.*

internal class TestFileSystem : FileSystem() {
    override val listeners = ArrayList<FileSystemListener>()
    private val _allFiles = HashSet<Entry>()

    val allFiles: Set<Entry> get() = _allFiles

    fun addFile(filePath: String, content: ByteArray = byteArrayOf()): Entry.File {
        var path = ""
        filePath.split("/").dropLast(1).forEach {
            path += it
            addEntry(Entry.Directory(path))
            path += "/"
        }

        val entry = Entry.File(filePath, content)
        addEntry(entry)
        return entry
    }

    @UseExperimental(ExperimentalStdlibApi::class)
    fun assertFileContent(filePath: String, expectedContent: String) {
        val entry: Entry.File? = allFiles.firstOrNull { it.path == filePath } as Entry.File?
        assertNotNull(entry, "File not found")
        assertEquals(expectedContent, entry.content.decodeToString())
    }

    override fun openImpl(filePath: String): Output {
        val entry: Entry.File? = allFiles.firstOrNull { it.path == filePath } as Entry.File?
        if (entry != null) return entry.open()

        return addFile(filePath).open()
    }

    override fun renameImpl(fromPath: String, toPath: String): Boolean {
        require(!toPath.startsWith(fromPath))
        require(fromPath != ".")

        when (val found = allFiles.single { it.path == fromPath }) {
            is Entry.File -> {
                replaceEntry(found, found.copy(toPath))
            }
            is Entry.Directory -> {
                allFiles.toList().forEach { entry ->
                    if (!entry.path.startsWith(fromPath)) return@forEach
                    val newEntryPath = entry.path.replaceFirst(fromPath, toPath)

                    when (entry) {
                        is Entry.File -> {
                            replaceEntry(entry, entry.copy(newEntryPath))
                        }
                        is Entry.Directory -> {
                            replaceEntry(entry, Entry.Directory(newEntryPath))
                        }
                    }
                }

                replaceEntry(found, Entry.Directory(toPath))
            }
        }

        return true
    }

    override fun deleteImpl(filePath: String) {
        when (val found = allFiles.single { it.path == filePath }) {
            is Entry.File -> check(_allFiles.remove(found))
            is Entry.Directory -> {
                check(allFiles.none { it.path.startsWith(filePath) && it != found })
                check(_allFiles.remove(found))
            }
        }
    }

    override fun list(directoryPath: String): List<String> {
        val normalized = directoryPath.removePrefix("./").takeIf { it != "." }?.plus("/") ?: ""
        return allFiles.filter {
            it.path.startsWith(normalized)
                && !it.path.drop(normalized.length).contains('/')
        }.map { it.path }
    }

    override fun size(file: String): Long {
        return (allFiles.single { it.path == file } as Entry.File).size
    }

    override fun lastModified(name: String): GMTDate {
        return (allFiles.single { it.path == name } as Entry.File).lastModified
    }

    override fun contains(path: String): Boolean {
        return path == "." || path == "" || allFiles.any { it.path == path }
    }

    sealed class Entry(val path: String) {
        class Directory(path: String) : Entry(path)

        class File(path: String, var content: ByteArray = byteArrayOf()) : Entry(path) {
            var closed = true
            var lastModified = GMTDate()
            val size: Long get() = content.size.toLong()

            fun open(): Output = object : AbstractOutput() {
                init {
                    closed = false
                }

                override fun closeDestination() {
                    closed = true
                }

                override fun flush(source: Memory, offset: Int, length: Int) {
                    check(!closed)
                    val newContent = ByteArray(content.size + length)
                    content.copyInto(newContent)
                    source.copyTo(newContent, offset, length, content.size)
                    content = newContent
                }
            }

            fun copy(newPath: String): File {
                return File(newPath).apply {
                    this.content = this@File.content
                    this.lastModified = this@File.lastModified
                    this.closed = this@File.closed
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            return other is Entry && other.path == path
        }

        override fun hashCode(): Int {
            return path.hashCode()
        }
    }

    private fun replaceEntry(old: Entry, new: Entry) {
        removeEntry(old)
        addEntry(new)
    }

    private fun removeEntry(entry: Entry) {
        if (_allFiles.remove(entry)) {
            listeners.forEach {
                it.fileRemoved(entry.path)
            }
        }
    }

    private fun addEntry(entry: Entry) {
        if (_allFiles.add(entry)) {
            listeners.forEach {
                it.fileCreated(entry.path)
            }
        }
    }
}
