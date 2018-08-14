package io.ktor.client.engine.okhttp

import kotlinx.coroutines.experimental.*
import okhttp3.*
import java.io.*

internal suspend fun OkHttpClient.execute(request: Request): Response = suspendCancellableCoroutine {
    val call = newCall(request)

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, cause: IOException) {
            it.resumeWithException(cause)
        }

        override fun onResponse(call: Call, response: Response) {
            it.resume(response)
        }
    })

    it.invokeOnCancellation { _ ->
        call.cancel()
    }
}