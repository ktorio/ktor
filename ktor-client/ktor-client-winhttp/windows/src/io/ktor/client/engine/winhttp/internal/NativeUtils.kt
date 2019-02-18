package io.ktor.client.engine.winhttp.internal

import kotlinx.coroutines.DisposableHandle

internal inline fun <T : DisposableHandle?, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        try {
            this?.dispose()
        } catch (ignored: Throwable) {
        }
    }
}
