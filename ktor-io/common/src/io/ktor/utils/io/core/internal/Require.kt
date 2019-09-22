package io.ktor.utils.io.core.internal

import kotlin.contracts.*

@PublishedApi
internal inline fun require(condition: Boolean, crossinline message: () -> String) {
    contract {
        returns() implies condition
    }

    if (!condition) {
        val m = object : RequireFailureCapture() {
            override fun doFail(): Nothing {
                throw IllegalArgumentException(message())
            }
        }
        m.doFail()
    }
}

@PublishedApi
internal abstract class RequireFailureCapture {
    abstract fun doFail(): Nothing
}
