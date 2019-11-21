/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

internal class CachedFilesView(
    private val fileSystem: FileSystem,
    public val pattern: FilePathPattern
) : FileSystemListener {
    private var knownFiles: MutableSet<String>? = null

    override fun fileCreated(path: String) {
        fileUpdated(path)
    }

    override fun fileUpdated(path: String) {
        val knownFiles = knownFiles

        if (knownFiles != null && pattern.matches(path)) {
            knownFiles.add(path)
        }
    }

    override fun fileRemoved(path: String) {
        knownFiles?.remove(path)
    }

    fun list(): List<String> = knownFiles?.toList() ?: refresh().toList()

    fun invalidate() {
        knownFiles = null
    }

    private fun refresh(): MutableSet<String> {
        return fileSystem.list(pattern).toMutableSet().also { knownFiles = it }
    }
}
