package org.jetbrains.ktor.content

import java.io.*

interface StreamContent {
    fun stream(out : OutputStream): Unit
}

/**
 * Does almost the same as [StreamContent] except it is suitable for async streaming so this is why it is preferred.
 */
interface StreamContentProvider {
    fun stream(): InputStream
}
