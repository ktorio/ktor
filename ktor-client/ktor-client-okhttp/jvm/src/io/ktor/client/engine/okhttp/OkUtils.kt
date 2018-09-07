package io.ktor.client.engine.okhttp

import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import kotlin.coroutines.*

internal suspend fun OkHttpClient.execute(request: Request): Response = suspendCancellableCoroutine {
    val call = newCall(request)
    val callback = object : Callback {

        override fun onFailure(call: Call, cause: IOException) {
            if (!call.isCanceled) it.resumeWithException(cause)
        }

        override fun onResponse(call: Call, response: Response) {
            if (!call.isCanceled) it.resume(response)
        }
    }

    call.enqueue(callback)

    it.invokeOnCancellation { _ ->
        call.cancel()
    }
}
