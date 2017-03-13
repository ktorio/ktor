package org.jetbrains.ktor.testing

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*

class TestApplicationRequest(
        var method: HttpMethod = HttpMethod.Get,
        var uri: String = "/",
        var version: String = "HTTP/1.1"
) : BaseApplicationRequest() {

    @Deprecated("Use primary constructor instead as HttpRequestLine is deprecated", level = DeprecationLevel.ERROR)
    constructor(requestLine: @Suppress("DEPRECATION") HttpRequestLine) : this(requestLine.method, requestLine.uri, requestLine.version)

    override val attributes = Attributes()

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

    var bodyBytes: ByteArray = ByteArray(0)
    var body: String
        get() = bodyBytes.toString(Charsets.UTF_8)
        set(newValue) {
            bodyBytes = newValue.toByteArray(Charsets.UTF_8)
        }

    var multiPartEntries: List<PartData> = emptyList()

    override val queryParameters by lazy { parseQueryString(queryString()) }

    private var headersMap: MutableMap<String, MutableList<String>>? = hashMapOf()
    fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name, { arrayListOf() }).add(value)
    }

    override val headers by lazy {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        valuesOf(map, caseInsensitiveKey = true)
    }

    override val content: RequestContent = object : RequestContent(this) {
        override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
        override fun getReadChannel() = bodyBytes.toReadChannel()

        override fun getMultiPartData(): MultiPartData = object : MultiPartData {
            override val parts: Sequence<PartData>
                get() = when {
                    isMultipart() -> multiPartEntries.asSequence()
                    else -> throw IOException("The request content is not multipart encoded")
                }
        }
    }

    override val cookies = RequestCookies(this)
}