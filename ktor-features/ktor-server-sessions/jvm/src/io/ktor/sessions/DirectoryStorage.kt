/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import java.io.*


/**
 * Creates a session storage that serializes them into regular files under the specified [rootDir]
 */
@KtorExperimentalAPI
fun directorySessionStorage(rootDir: File, cached: Boolean = true): SessionStorage = when (cached) {
    true -> CacheStorage(DirectoryStorage(rootDir), 60000)
    false -> DirectoryStorage(rootDir)
}

internal class DirectoryStorage(private val dir: File) : SessionStorage, Closeable {
    init {
        dir.mkdirsOrFail()
    }

    override fun close() {
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        requireId(id)
        val file = fileOf(id)

        file.parentFile?.mkdirsOrFail()
        coroutineScope {
            provider(file.writeChannel(coroutineContext = coroutineContext))
        }
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        requireId(id)
        try {
            val file = fileOf(id)

            file.parentFile?.mkdirsOrFail()
            return consumer(file.readChannel())
        } catch (notFound: FileNotFoundException) {
            throw NoSuchElementException("No session data found for id $id")
        }
    }

    override suspend fun invalidate(id: String) {
        requireId(id)
        try {
            val file = fileOf(id)
            file.delete()
            file.parentFile?.deleteParentsWhileEmpty(dir)
        } catch (notFound: FileNotFoundException) {
            throw NoSuchElementException("No session data found for id $id")
        }
    }

    private fun fileOf(id: String) = File(dir, split(id).joinToString(File.separator, postfix = ".dat"))
    private fun split(id: String) = id.windowedSequence(size = 2, step = 2, partialWindows = true)

    private fun requireId(id: String) {
        if (id.isEmpty()) {
            throw IllegalArgumentException("Session id is empty")
        }
        if (id.indexOfAny(listOf("..", "/", "\\", "!", "?", ">", "<", "\u0000")) != -1) {
            throw IllegalArgumentException("Bad session id $id")
        }
    }
}

private fun File.mkdirsOrFail() {
    if (!this.mkdirs() && !this.exists()) {
        throw IOException("Couldn't create directory $this")
    }
    if (!this.isDirectory) {
        throw IOException("Path is not a directory: $this")
    }
}

private tailrec fun File.deleteParentsWhileEmpty(mostTop: File) {
    if (this != mostTop && isDirectory && exists() && list().isNullOrEmpty()) {
        if (!delete() && exists()) {
            throw IOException("Failed to delete dir $this")
        }

        parentFile.deleteParentsWhileEmpty(mostTop)
    }
}
