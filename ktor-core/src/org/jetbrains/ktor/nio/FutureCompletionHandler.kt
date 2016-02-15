package org.jetbrains.ktor.nio

import java.nio.channels.*
import java.util.concurrent.*

class FutureCompletionHandler<T, A>(val future: CompletableFuture<T>) : CompletionHandler<T, A> {
    override fun completed(result: T, attachment: A) {
        future.complete(result)
    }

    override fun failed(exc: Throwable, attachment: A) {
        future.completeExceptionally(exc)
    }
}