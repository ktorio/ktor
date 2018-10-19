package io.ktor.server.engine

import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * Stop server on job cancellation. The returned deferred need to be completed or cancelled.
 */
@EngineAPI
@KtorExperimentalAPI
fun ApplicationEngine.stopServerOnCancellation(): CompletableDeferred<Unit> {
    return environment.parentCoroutineContext[Job]?.launchOnCancellation {
        stop(1, 5, TimeUnit.SECONDS)
    } ?: CompletableDeferred()
}

/**
 * Launch a coroutine with [block] body when the parent job is cancelled or a returned deferred is cancelled.
 * It is important to complete or cancel returned deferred
 * otherwise the parent job will be unable to complete successfully.
 */
@InternalAPI
fun Job.launchOnCancellation(block: suspend (cause: Throwable) -> Unit): CompletableDeferred<Unit> {
    val deferred = CompletableDeferred<Unit>(parent = this)

    GlobalScope.launch(this + Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            deferred.await()
        } catch (t: Throwable) {
            block(t)
        }
    }

    return deferred
}
