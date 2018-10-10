package io.ktor.client.engine.okhttp

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.Headers
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import okhttp3.*
import kotlin.coroutines.*

internal class OkHttpResponse(
    private val response: Response,
    override val call: HttpClientCall,
    override val requestTime: GMTDate,
    override val content: ByteReadChannel,
    override val coroutineContext: CoroutineContext
) : HttpResponse {

    override val headers: Headers = object : Headers {
        override val caseInsensitiveName: Boolean = false
        private val instance = response.headers()!!

        override fun getAll(name: String): List<String>? = instance.values(name)

        override fun names(): Set<String> = instance.names()

        override fun entries(): Set<Map.Entry<String, List<String>>> = instance.toMultimap().entries

        override fun isEmpty(): Boolean = instance.size() == 0
    }

    override val status: HttpStatusCode = HttpStatusCode(response.code(), response.message())

    override val version: HttpProtocolVersion = response.protocol().fromOkHttp()

    override val responseTime: GMTDate = GMTDate()
}

@Suppress("DEPRECATION")
private fun Protocol.fromOkHttp(): HttpProtocolVersion = when (this) {
    Protocol.HTTP_1_0 -> HttpProtocolVersion.HTTP_1_0
    Protocol.HTTP_1_1 -> HttpProtocolVersion.HTTP_1_1
    Protocol.SPDY_3 -> HttpProtocolVersion.SPDY_3
    Protocol.HTTP_2 -> HttpProtocolVersion.HTTP_2_0
    Protocol.H2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
    Protocol.QUIC -> HttpProtocolVersion.QUIC
}
