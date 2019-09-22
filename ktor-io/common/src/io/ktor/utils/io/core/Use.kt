package io.ktor.utils.io.core

inline fun <I : Input, R> I.use(block: (I) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}

inline fun <O : Output, R> O.use(block: (O) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}
