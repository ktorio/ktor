package io.ktor.util.cio

import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("KDocMissingDocumentation")
@Deprecated("Will become private")
object NoopContinuation : Continuation<Any?> {
    override fun resumeWith(result: Result<Any?>) {}

    override val context: CoroutineContext = Dispatchers.Unconfined
}
