package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.response.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.jvm.javaio.*
import java.io.*

internal actual fun HttpClient.platformDefaultTransformers() {
    responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, response) ->
        if (response !is HttpResponse) return@intercept
        when (info.type) {
            InputStream::class -> {
                val stream = response.content.toInputStream(response.coroutineContext[Job])
                proceedWith(HttpResponseContainer(info, stream))
            }
        }
    }
}
