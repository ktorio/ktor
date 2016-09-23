package org.jetbrains.ktor.pipeline

import java.io.*

fun PipelineContext<*>.closeAtEnd(vararg closeables: Closeable) {
    onFinish {
        for (closeable in closeables) {
            closeable.closeQuietly()
        }
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (ignore: IOException) {
    }
}

