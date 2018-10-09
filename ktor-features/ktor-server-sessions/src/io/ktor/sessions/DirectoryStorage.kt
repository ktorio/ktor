package io.ktor.sessions

import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*
import java.io.*


/**
 * Creates a session storage that serializes them into regular files under the specified [rootDir]
 */
@KtorExperimentalAPI
fun directorySessionStorage(rootDir: File, cached: Boolean = true): SessionStorage = when (cached) {
    true -> CacheStorage(DirectoryStorage(rootDir), 60000)
    false -> DirectoryStorage(rootDir)
}

internal class DirectoryStorage(val dir: File) : SessionStorage, Closeable {
    init {
        dir.mkdirsOrFail()
    }

    override fun close() {
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        requireId(id)
        val file = fileOf(id)

        file.parentFile?.mkdirsOrFail()
        provider(file.writeChannel())
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
    private fun split(id: String) = id.window(2)

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

private fun <T> Array<T>?.isNullOrEmpty() = this == null || this.isEmpty()

private fun String.window(size: Int, step: Int = size, dropTrailing: Boolean = false): Sequence<String> =
        if (isEmpty() || (size > length && dropTrailing)) emptySequence()
        else object : Sequence<String> {
            override fun iterator(): Iterator<String> = StringWindowIterator(this@window, size, step, dropTrailing)
        }

private class StringWindowIterator(val string: String, val size: Int, val step: Int, val dropTrailing: Boolean) : AbstractIterator<String>() {
    var currentIndex = 0

    init {
        require(step > 0)
        require(size > 0)
    }

    override fun computeNext() {
        if (currentIndex >= string.length) {
            done()
            return
        }

        val endExclusive = currentIndex + size
        if (endExclusive > string.length && dropTrailing) {
            done()
            return
        }

        setNext(string.substring(currentIndex, Math.min(endExclusive, string.length)))
        currentIndex = endExclusive
    }
}
