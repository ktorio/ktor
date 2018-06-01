package io.ktor.compat

interface Closeable {
    fun close()
}

interface AutoCloseable : Closeable

inline fun <T : Closeable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}
