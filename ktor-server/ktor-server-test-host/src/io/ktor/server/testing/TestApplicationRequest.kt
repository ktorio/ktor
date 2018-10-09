package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.util.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

class TestApplicationRequest constructor(
        call: TestApplicationCall,
        var method: HttpMethod = HttpMethod.Get,
        var uri: String = "/",
        var version: String = "HTTP/1.1"
) : BaseApplicationRequest(call), CoroutineScope by call {
    var protocol: String = "http"

    override val local = object : RequestConnectionPoint {
        override val uri: String
            get() = this@TestApplicationRequest.uri

        override val method: HttpMethod
            get() = this@TestApplicationRequest.method

        override val scheme: String
            get() = protocol

        override val port: Int
            get() = header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt() ?: 80

        override val host: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: "localhost"

        override val remoteHost: String
            get() = "localhost"

        override val version: String
            get() = this@TestApplicationRequest.version
    }

    @Volatile
    var bodyChannel: ByteReadChannel = ByteReadChannel.Empty

    @Deprecated("Use setBody() method instead", ReplaceWith("setBody()"), level = DeprecationLevel.ERROR)
    var bodyBytes: ByteArray
        @Deprecated("TestApplicationEngine no longer supports bodyBytes.get()", level = DeprecationLevel.ERROR)
        get() = error("TestApplicationEngine no longer supports bodyBytes.get()")
        set(value) { setBody(value) }

    @Deprecated("Use setBody() method instead", ReplaceWith("setBody()"), level = DeprecationLevel.ERROR)
    var body: String
        @Deprecated("TestApplicationEngine no longer supports body.get()", level = DeprecationLevel.ERROR)
        get() = error("TestApplicationEngine no longer supports body.get()")
        set(value) { setBody(value) }

    @Deprecated(
            message = "multiPartEntries is deprecated, use setBody() method instead",
            replaceWith = ReplaceWith("setBody()"), level = DeprecationLevel.ERROR
    )
    var multiPartEntries: List<PartData> = listOf()

    override val queryParameters by lazy(LazyThreadSafetyMode.NONE) { parseQueryString(queryString()) }

    override val cookies = RequestCookies(this)

    private var headersMap: MutableMap<String, MutableList<String>>? = hashMapOf()

    fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name, { arrayListOf() }).add(value)
    }

    override val headers: Headers by lazy(LazyThreadSafetyMode.NONE) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        Headers.build {
            map.forEach { (name, values) ->
                appendAll(name, values)
            }
        }
    }

    override fun receiveChannel(): ByteReadChannel = bodyChannel
}

fun TestApplicationRequest.setBody(value: String) {
    setBody(value.toByteArray())
}

fun TestApplicationRequest.setBody(value: ByteArray) {
    bodyChannel = ByteReadChannel(value)
}

private suspend fun WriterScope.append(str: String, charset: Charset = Charsets.UTF_8) {
    channel.writeFully(str.toByteArray(charset))
}

fun TestApplicationRequest.setBody(boundary: String, parts: List<PartData>): Unit {
    bodyChannel = writer(Dispatchers.IO) {
        if (parts.isEmpty()) return@writer

        try {
            append("\r\n\r\n")
            parts.forEach {
                append("--$boundary\r\n")
                for ((key, values) in it.headers.entries()) {
                    append("$key: ${values.joinToString(";")}\r\n")
                }
                append("\r\n")
                when (it) {
                    is PartData.FileItem -> it.provider().asStream().copyTo(channel.toOutputStream())
                    is PartData.FormItem -> append(it.value)
                }
                append("\r\n")
            }

            append("--$boundary--\r\n\r\n")
        } finally {
            parts.forEach { it.dispose() }
        }
    }.channel
}
