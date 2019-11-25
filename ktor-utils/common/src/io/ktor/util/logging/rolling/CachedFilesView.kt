/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import kotlin.jvm.*

internal class CachedFilesView(
    private val fileSystem: FileSystem,
    public val pattern: FilePathPattern,
    private val listener: Listener?
) : FileSystemListener {
    private var knownFiles: MutableMap<String, CachedFile>? = null
    private var knownFilesList: List<CachedFile> = emptyList()

    override fun fileCreated(path: String) {
        fileUpdated(path)
    }

    override fun fileUpdated(path: String) {
        val knownFiles = knownFiles

        if (knownFiles != null && pattern.matches(path)) {
            val size = fileSystem.size(path)
            val lastModified = fileSystem.lastModified(path)

            knownFiles.getOrPut(path) { CachedFile(path, size, lastModified).also { knownFilesList += it } }
                .let { file ->
                    file.knownSize = size
                    file.lastModified = lastModified
                    listener?.changed(file)
                }
        }
    }

    override fun fileRemoved(path: String) {
        knownFiles?.remove(path)?.let { removed ->
            knownFilesList -= removed
            removed.existing = false
            listener?.changed(removed)
        }
    }

    fun list(): List<CachedFile> = when (knownFiles) {
        null -> refresh()
        else -> knownFilesList
    }

    fun listPaths(): List<String> = list().map { it.path }

    fun invalidate() {
        knownFiles = null
        knownFilesList = emptyList()
    }

    private fun refresh(): List<CachedFile> {
        val found = fileSystem.list(pattern).map { path ->
            CachedFile(path, fileSystem.size(path), fileSystem.lastModified(path))
        }.toList()

        knownFilesList = found
        knownFiles = found.associateByTo(HashMap()) { it.path }

        listener?.let { listener ->
            found.forEach {
                listener.changed(it)
            }
        }

        return found
    }

    class CachedFile internal constructor(val path: String, knownSize: Long, modificationDate: GMTDate) {
        var existing: Boolean = true
            internal set

        @Volatile
        var knownSize: Long = knownSize
            internal set

        @Volatile
        var lastModified: GMTDate = modificationDate
            internal set

        override fun equals(other: Any?): Boolean = other is CachedFile && other.path == path
        override fun hashCode(): Int = path.hashCode()
        override fun toString(): String = "CachedFile($path)"
    }

    internal interface Listener {
        fun changed(file: CachedFile)
    }
}
