package org.jetbrains.ktor.pipeline

import java.io.*

suspend fun PipelineContext<*>.closeAtEnd(vararg closeables: Closeable) {
    proceed()
    for (closeable in closeables) {
        closeable.closeQuietly()
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (ignore: IOException) {
    }
}

