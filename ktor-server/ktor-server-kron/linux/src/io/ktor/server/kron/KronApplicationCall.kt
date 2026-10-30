/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.parseQueryString
import io.ktor.http.withEmptyStringForValuelessKeys
import io.ktor.server.application.Application
import io.ktor.server.application.PipelineCall
import io.ktor.server.engine.BaseApplicationCall
import io.ktor.server.engine.BaseApplicationRequest
import io.ktor.server.engine.BaseApplicationResponse
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.encodeParameters
import io.ktor.server.response.ResponseHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineScope
import kron.TcpConnection
import kron.glyph11.RequestHead
import kotlin.coroutines.CoroutineContext

internal class KronApplicationCall(
    application: Application,
    session: Http1Session,
    connection: TcpConnection,
    body: RequestBody?,
    version10: Boolean,
    upgradeRequested: Boolean,
    override val coroutineContext: CoroutineContext,
) : BaseApplicationCall(application), CoroutineScope {
    override val request = KronApplicationRequest(this, session, connection, body, version10)
    override val response =
        KronApplicationResponse(this, session, request.local.method == HttpMethod.Head, upgradeRequested)

    init {
        putResponseAttribute()
    }
}

/** A request read from the session's head: its strings are made when Ktor asks for them, but the target's. */
internal class KronApplicationRequest(
    call: PipelineCall,
    private val session: Http1Session,
    connection: TcpConnection,
    body: RequestBody?,
    version10: Boolean,
) : BaseApplicationRequest(call) {
    private val bytes = session.headBytes
    private val head = session.head

    override val engineHeaders: Headers = KronHeaders(bytes, head)

    override val engineReceiveChannel: ByteReadChannel = body ?: ByteReadChannel.Empty

    override val local: RequestConnectionPoint = KronConnectionPoint(
        connection,
        if (version10) "HTTP/1.0" else "HTTP/1.1",
        bytes.decodeToString(head.pathStart, head.queryStart + head.queryLength),
        methodOf(bytes, head.methodStart, head.methodLength),
        engineHeaders,
    )

    private var cookieJar: RequestCookies? = null
    override val cookies: RequestCookies
        get() = cookieJar ?: RequestCookies(this).also { cookieJar = it }

    private var raw: Parameters? = null
    override val rawQueryParameters: Parameters
        get() {
            raw?.let { return it }
            val parsed = if (head.queryLength == 0) Parameters.Empty else parseQueryString(query, decode = false)
            raw = parsed
            return parsed
        }

    private var decoded: Parameters? = null

    @OptIn(InternalAPI::class)
    override val queryParameters: Parameters
        get() = decoded ?: encodeParameters(rawQueryParameters).withEmptyStringForValuelessKeys().also { decoded = it }

    private val query: String get() = local.uri.substring(head.queryStart - head.pathStart)
}

/** The response, written into the connection's write slab by the session. */
internal class KronApplicationResponse(
    call: KronApplicationCall,
    private val session: Http1Session,
    private val isHead: Boolean,
    private val upgradeRequested: Boolean,
) : BaseApplicationResponse(call) {
    private var statusCode = HttpStatusCode.OK
    private val names = ArrayList<String>(8)
    private val values = ArrayList<String>(8)
    private var body: ResponseBody? = null
    private var sent = false

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            names.add(name)
            values.add(value)
        }

        override fun get(name: String): String? {
            for (i in names.indices) {
                if (names[i].equals(name, ignoreCase = true)) {
                    return values[i]
                }
            }

            return null
        }

        override fun getEngineHeaderNames(): List<String> = names.distinctBy { it.lowercase() }

        override fun getEngineHeaderValues(name: String): List<String> =
            names.indices.filter { names[it].equals(name, ignoreCase = true) }.map { values[it] }
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        this.statusCode = statusCode
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        headers[HttpHeaders.ContentLength]?.toLong()?.let { length ->
            if (length != bytes.size.toLong()) {
                throw IllegalStateException("Content-Length $length, body ${bytes.size} bytes")
            }
        }

        session.sending {
            session.writeHead(statusCode, names, values)
            if (!isHead) {
                session.write(bytes)
            }
        }

        sent = true
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        session.sending { session.writeHead(statusCode, names, values) }
        sent = true
    }

    // The head goes out at once, as CIO sends it: a body that is slow to come, or comes only once the request body
    // has, must not keep the client waiting for the status.
    override suspend fun responseChannel(): ByteWriteChannel {
        session.sending {
            session.writeHead(statusCode, names, values)
            session.flush()
        }

        val channel = ResponseBody(
            session,
            chunked = headers[HttpHeaders.TransferEncoding] == "chunked",
            contentLength = headers[HttpHeaders.ContentLength]?.toLong() ?: -1,
            discard = isHead,
        )
        body = channel
        return channel
    }

    // Ktor's own sends this through Dispatchers.IO; here the writer runs on the reactor, as the rest of the call.
    override suspend fun respondWriteChannelContent(content: OutgoingContent.WriteChannelContent) {
        val channel = responseChannel()
        try {
            content.writeTo(channel)
        } catch (e: Throwable) {
            channel.cancel(e)
            throw e
        }

        channel.flushAndClose()
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        check(upgradeRequested) {
            "Unable to perform upgrade as it is not requested by the client: " +
                "request should have Upgrade and Connection headers filled properly"
        }

        session.sending {
            session.writeHead(statusCode, names, values)
            session.flush()
        }
        session.mustClose = true

        // The connection is the protocol's now: its bytes in and out, until the upgraded job ends.
        val input = RequestBody(session, chunked = false, remaining = 0, expectContinue = false, raw = true)
        val output = ResponseBody(session, chunked = false, contentLength = -1, discard = false)
        val context = (call as KronApplicationCall).coroutineContext
        try {
            upgrade.upgrade(input, output, context, context).join()
        } finally {
            output.flushAndClose()
            input.cancel(null)
            sent = true
        }
    }

    /** Ends the response once its call has returned: false if it was not sent whole. */
    internal fun finish(): Boolean {
        val body = body ?: return sent
        if (!body.isClosedForWrite) {
            body.cancel(IllegalStateException("The response body was left open"))
        }

        return body.isComplete
    }
}

/** The request's headers, looked up in the head's bytes; a value becomes a string when it is asked for. */
internal class KronHeaders(private val bytes: ByteArray, private val head: RequestHead) : Headers {
    override val caseInsensitiveName: Boolean get() = true

    override fun get(name: String): String? {
        val i = indexOf(name, 0)
        return if (i < 0) null else value(i)
    }

    override fun getAll(name: String): List<String>? {
        var i = indexOf(name, 0)
        if (i < 0) {
            return null
        }

        val all = ArrayList<String>(1)
        while (i >= 0) {
            all.add(value(i))
            i = indexOf(name, i + 1)
        }

        return all
    }

    override fun contains(name: String): Boolean = indexOf(name, 0) >= 0

    override fun names(): Set<String> = (0 until head.headerCount).mapTo(LinkedHashSet()) { name(it) }

    override fun entries(): Set<Map.Entry<String, List<String>>> =
        Headers.build { for (i in 0 until head.headerCount) append(name(i), value(i)) }.entries()

    override fun isEmpty(): Boolean = head.headerCount == 0

    private fun name(i: Int): String =
        bytes.decodeToString(head.headerNameStart(i), head.headerNameStart(i) + head.headerNameLength(i))

    private fun value(i: Int): String {
        val start = head.headerValueStart(i)
        var end = start + head.headerValueLength(i)
        while (end > start && (bytes[end - 1] == ' '.code.toByte() || bytes[end - 1] == '\t'.code.toByte())) {
            end--
        }

        return bytes.decodeToString(start, end)
    }

    private fun indexOf(name: String, from: Int): Int {
        for (i in from until head.headerCount) {
            if (head.headerNameLength(i) == name.length && nameEquals(head.headerNameStart(i), name)) {
                return i
            }
        }

        return -1
    }

    private fun nameEquals(at: Int, name: String): Boolean {
        for (j in name.indices) {
            var b = bytes[at + j].toInt()
            var c = name[j].code
            if (b in 'A'.code..'Z'.code) b += 32
            if (c in 'A'.code..'Z'.code) c += 32
            if (b != c) {
                return false
            }
        }

        return true
    }
}

internal class KronConnectionPoint(
    private val connection: TcpConnection,
    override val version: String,
    override val uri: String,
    override val method: HttpMethod,
    private val headers: Headers,
) : RequestConnectionPoint {
    override val scheme: String get() = "http"

    private val hostHeader: String? get() = headers[HttpHeaders.Host]

    @Deprecated("Use localPort or serverPort instead", level = DeprecationLevel.ERROR)
    override val port: Int get() = localPort

    @Deprecated("Use localHost or serverHost instead", level = DeprecationLevel.ERROR)
    override val host: String get() = localHost

    override val localPort: Int get() = connection.localAddress?.port ?: URLProtocol.HTTP.defaultPort

    override val serverPort: Int
        get() = hostHeader?.substringAfterLast(":", "")?.toIntOrNull() ?: localPort

    override val localHost: String get() = connection.localAddress?.host ?: "localhost"

    override val serverHost: String get() = hostHeader?.substringBeforeLast(":") ?: localHost

    override val localAddress: String get() = connection.localAddress?.host ?: "localhost"

    override val remoteHost: String get() = connection.remoteAddress?.host ?: "unknown"

    override val remotePort: Int get() = connection.remoteAddress?.port ?: 0

    override val remoteAddress: String get() = connection.remoteAddress?.host ?: "unknown"

    override fun toString(): String = "KronConnectionPoint(uri=$uri, method=$method, version=$version)"
}

// The standard methods without a string made for them.
private fun methodOf(bytes: ByteArray, start: Int, length: Int): HttpMethod {
    fun eq(name: String): Boolean {
        if (length != name.length) return false
        for (i in 0 until length) if (bytes[start + i].toInt() != name[i].code) return false
        return true
    }

    return when {
        eq("GET") -> HttpMethod.Get
        eq("POST") -> HttpMethod.Post
        eq("PUT") -> HttpMethod.Put
        eq("DELETE") -> HttpMethod.Delete
        eq("HEAD") -> HttpMethod.Head
        eq("PATCH") -> HttpMethod.Patch
        eq("OPTIONS") -> HttpMethod.Options
        else -> HttpMethod.parse(bytes.decodeToString(start, start + length))
    }
}
