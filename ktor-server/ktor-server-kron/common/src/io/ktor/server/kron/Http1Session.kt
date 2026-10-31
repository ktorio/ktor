/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.handleFailure
import io.ktor.util.cio.ChannelWriteException
import io.ktor.util.pipeline.execute
import io.ktor.utils.io.ClosedWriteChannelException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kron.IOException
import kron.Memory
import kron.RecvItem
import kron.TcpConnection
import kron.glyph11.Glyph11
import kron.glyph11.HttpParseException
import kron.glyph11.ParserLimits
import kron.glyph11.RequestHead
import kron.tls.TlsService
import kron.tls.TlsSession

/**
 * One connection's HTTP/1.1, on its reactor's thread: requests parsed by Glyph11 one after another, each run
 * through Ktor's pipeline inline, the responses staged in the connection's write slab. They go out together when
 * the next request has not all arrived yet, so pipelined requests are answered with one send.
 *
 * The bytes received and not yet consumed are [carry]'s `[start, end)`. A request's head is copied out of it before
 * its call runs, so a call can read its head however long it runs while its body moves on through the carry.
 *
 * On a TLS connector the connection starts with [tlsService]'s handshake; from then on the carry holds plaintext,
 * decrypted as it arrives, and everything written goes through the [TlsSession], to BoringSSL or the kernel.
 */
internal class Http1Session(
    private val conn: TcpConnection,
    private val engine: KronApplicationEngine,
    private val tlsService: TlsService? = null,
) {
    internal var carry = ByteArray(INITIAL_CARRY)
    internal var start = 0
    internal var end = 0
    private val item = RecvItem()
    private var peerGone = false

    /** Received bytes the session has not consumed yet. */
    internal val available: Int get() = end - start

    /** The current request's head, at offset 0, and its parse; the session's own, reused by the next request. */
    internal var headBytes = ByteArray(INITIAL_HEAD)
        private set
    internal val head = RequestHead()

    /** The connection ends after the current response: asked for, or its framing can no longer be trusted. */
    internal var mustClose = false

    /** The current response's head is staged: a `100 Continue` would come too late. */
    private var headWritten = false

    // What this connection's calls start is a child of its job, so the engine's stop can wait for it, and cancel it.
    // Under its reactor's job, which no other thread adds to.
    private val job = Job(conn.reactor.handlerContext[Job])
    private val context = conn.reactor.handlerContext + job

    private var tls: TlsSession? = null

    /** The connection came in on a TLS connector. */
    internal val secure: Boolean get() = tlsService != null

    suspend fun run() {
        try {
            if (tlsService != null) {
                val session = tlsService.accept(conn)
                tls = session
                // A request can ride in with the handshake's last flight
                appendPlaintext(session, session.drainPlaintext())
            }

            while (true) {
                if (!awaitHead()) {
                    return
                }

                serve()
                if (mustClose || conn.isClosed) {
                    return
                }
            }
        } catch (_: CancellationException) {
            // the engine is stopping
        } catch (_: IOException) {
            // A failed handshake, or a record that does not decrypt: the connection ends, as when the peer leaves.
        } finally {
            // What the last requests staged goes out before the handler lets go and kron sends its FIN, and after it
            // TLS's close_notify.
            flushIfIdle()
            tls?.close()
            job.complete()
        }
    }

    // ---- Requests -----------------------------------------------------------------------------------------

    // Waits for the next request's head and copies it to headBytes; false when the peer left first or the request
    // was refused. Glyph11 runs only once a CRLFCRLF has arrived, which ends every head it accepts: a head that
    // trickles in is scanned once rather than parsed again on every recv.
    // Inline, as serve: a suspend function makes its frame on every call, and run's is made once a connection.
    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun awaitHead(): Boolean {
        val limits = ParserLimits.Default
        var scanned = 0
        while (true) {
            val held = end - start
            val headEnd = indexOfCrlfCrlf(carry, start + maxOf(scanned - 3, 0), end)
            if (headEnd >= 0 || held >= limits.maxTotalHeaderBytes) {
                val length = if (headEnd >= 0) headEnd + 4 - start else held
                if (headBytes.size < length) {
                    headBytes = ByteArray(maxOf(length, headBytes.size * 2))
                }

                carry.copyInto(headBytes, 0, start, start + length)
                try {
                    Glyph11.parse(headBytes, 0, length, head, limits)
                } catch (e: HttpParseException) {
                    refuse(e.statusCode)
                    return false
                }

                start += length
                return true
            }

            scanned = held
            if (start == end && carry.size > MAX_IDLE_CARRY) {
                carry = ByteArray(INITIAL_CARRY)
                start = 0
                end = 0
            }

            if (!fill()) {
                return false
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun serve() {
        headWritten = false
        val bytes = headBytes
        val version10 = bytes[head.versionStart + 7] == '0'.code.toByte()
        val connection = head.findHeader(bytes, CONNECTION)
        mustClose = if (version10) {
            connection < 0 || !head.hasToken(bytes, connection, KEEP_ALIVE)
        } else {
            connection >= 0 && head.hasToken(bytes, connection, CLOSE)
        } || conn.reactor.draining

        val body = when (head.bodyFraming) {
            RequestHead.BODY_NONE -> null

            else -> {
                val expect = head.findHeader(bytes, EXPECT)
                val expectContinue = !version10 && expect >= 0 && head.valueEquals(bytes, expect, CONTINUE_100)
                RequestBody(this, head.bodyFraming == RequestHead.BODY_CHUNKED, head.contentLength, expectContinue)
            }
        }

        val call = KronApplicationCall(
            engine.application(),
            this,
            conn,
            body,
            version10,
            upgradeRequested = connection >= 0 && head.hasToken(bytes, connection, UPGRADE),
            coroutineContext = context,
        )

        try {
            engine.pipeline.execute(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            try {
                handleFailure(call, e)
            } catch (_: Throwable) {
                mustClose = true
            }
        }

        // A response cut short, or a body left unread, leaves nothing to find the next request by.
        if (!call.response.finish() || (body != null && !body.isComplete)) {
            mustClose = true
        }
    }

    // A request Glyph11 refused: its status, then the connection ends.
    private suspend fun refuse(status: Int) {
        val code = HttpStatusCode.fromValue(status)
        val line = statusLine(code)
        put(line, 0, line.size)
        put(REFUSAL_HEADERS, 0, REFUSAL_HEADERS.size)
        conn.flush()
        mustClose = true
    }

    // ---- Receiving ----------------------------------------------------------------------------------------

    /**
     * Waits until more bytes are in the carry; false once the peer is gone and nothing more came. What the responses
     * so far staged goes out first: nothing more is answered until the peer sends more.
     */
    internal suspend fun fill(): Boolean {
        while (!peerGone) {
            flushIfIdle()
            val snapshot = conn.read()
            // Counted, not read off end: taking an item can move the carry's bytes to its front. Under TLS a recv
            // can complete no record, and then brings nothing.
            var received = 0
            while (conn.tryGetItem(snapshot, item)) {
                received += take(item)
            }

            if (snapshot.isClosed || tls?.closed == true) {
                peerGone = true
            }

            if (received > 0) {
                return true
            }
        }

        return false
    }

    // Appends what item brought to the carry, decrypted on a TLS connection; returns how many bytes that was.
    private fun take(item: RecvItem): Int {
        try {
            val tls = tls
            if (tls != null) {
                return if (item.hasBuffer) appendPlaintext(tls, tls.decrypt(item.address, item.length)) else 0
            }

            val length = item.length
            if (carry.size - end < length) {
                makeRoom(length)
            }

            item.copyTo(carry, end, 0, length)
            end += length
            return length
        } finally {
            conn.returnBuffer(item)
        }
    }

    // Appends the length bytes the session's last decrypt left at its plaintext.
    private fun appendPlaintext(session: TlsSession, length: Int): Int {
        if (length > 0) {
            if (carry.size - end < length) {
                makeRoom(length)
            }

            Memory.copy(session.plaintext, carry, end, length)
            end += length
        }

        return length
    }

    // Moves the unconsumed bytes to the front, and grows the carry when that still leaves no room.
    private fun makeRoom(length: Int) {
        val held = end - start
        if (held + length > carry.size) {
            val grown = ByteArray(maxOf(carry.size * 2, held + length))
            carry.copyInto(grown, 0, start, end)
            carry = grown
        } else {
            carry.copyInto(carry, 0, start, end)
        }

        start = 0
        end = held
    }

    /** `100 Continue`, for a request that waits for it before sending its body - unless its response has begun. */
    internal suspend fun sendContinue() {
        sending {
            if (!headWritten) {
                put(CONTINUE, 0, CONTINUE.size)
                flush()
            }
        }
    }

    // ---- Sending ------------------------------------------------------------------------------------------

    // One sender at a time: a call can stream its response while a coroutine of its own reads the request body, and
    // kron takes no write while a flush is out.
    internal val sendLock = Mutex()

    /** Runs [block], which stages and may flush, as the only sender. */
    internal suspend inline fun <T> sending(block: () -> T): T = sendLock.withLock { block() }

    // Sends what earlier responses staged, unless a sender is at work: that one sends what it stages itself. Inline,
    // as it runs before every recv.
    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun flushIfIdle() {
        if (conn.writtenBytes == 0 || conn.isClosed || !sendLock.tryLock()) {
            return
        }

        try {
            conn.flush()
        } finally {
            sendLock.unlock()
        }
    }

    private var out = ByteArray(INITIAL_OUT)

    /**
     * Stages the status line and the headers, [fields] being name, value, name, value...; a connection that must close
     * says so, unless the response did.
     */
    internal fun writeHead(status: HttpStatusCode, fields: List<String>) {
        val line = statusLine(status)
        var out = out
        line.copyInto(out)
        var p = line.size
        var hasConnection = false
        for (i in 0 until fields.size / 2) {
            val name = fields[2 * i]
            val value = fields[2 * i + 1]
            val need = p + name.length + value.length + 4
            if (need > out.size) {
                out = out.copyOf(maxOf(out.size * 2, need + 64))
                this.out = out
            }

            p = putAscii(out, p, name)
            out[p++] = ':'.code.toByte()
            out[p++] = ' '.code.toByte()
            p = putAscii(out, p, value)
            out[p++] = '\r'.code.toByte()
            out[p++] = '\n'.code.toByte()

            if (sameName(name, "Connection")) {
                hasConnection = true
                if (value.contains("close", ignoreCase = true)) {
                    mustClose = true
                }
            }
        }

        if (out.size < p + CONNECTION_CLOSE.size + 2) {
            out = out.copyOf(p + CONNECTION_CLOSE.size + 2)
            this.out = out
        }

        if (mustClose && !hasConnection) {
            CONNECTION_CLOSE.copyInto(out, p)
            p += CONNECTION_CLOSE.size
        }

        out[p++] = '\r'.code.toByte()
        out[p++] = '\n'.code.toByte()
        put(out, 0, p)
        headWritten = true
    }

    internal fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        put(bytes, offset, length)
    }

    // Stages bytes for the peer; on a TLS connection the session encrypts them, or leaves that to the kernel.
    private fun put(bytes: ByteArray, offset: Int, length: Int) {
        val tls = tls
        if (tls == null) {
            conn.write(bytes, offset, length)
        } else {
            tls.write(conn, bytes, offset, length)
        }
    }

    /** Sends what is staged; throws once the peer is gone, so a streaming writer stops. */
    internal suspend fun flush() {
        conn.flush()
        if (conn.isClosed) {
            throw ChannelWriteException("The peer closed the connection", ClosedWriteChannelException())
        }
    }

    internal val writtenBytes: Int get() = conn.writtenBytes

    private companion object {
        const val INITIAL_CARRY = 4096
        const val MAX_IDLE_CARRY = 64 * 1024
        const val INITIAL_HEAD = 1024
        const val INITIAL_OUT = 512

        val CONNECTION = "connection".encodeToByteArray()
        val EXPECT = "expect".encodeToByteArray()
        val CLOSE = "close".encodeToByteArray()
        val KEEP_ALIVE = "keep-alive".encodeToByteArray()
        val UPGRADE = "upgrade".encodeToByteArray()
        val CONTINUE_100 = "100-continue".encodeToByteArray()

        val CONTINUE = "HTTP/1.1 100 Continue\r\n\r\n".encodeToByteArray()
        val CONNECTION_CLOSE = "Connection: close\r\n".encodeToByteArray()
        val REFUSAL_HEADERS = "Content-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray()
    }
}

// The status lines of the codes Ktor knows, made once; any other code or reason phrase is made when it is sent.
private val statusLines = arrayOfNulls<ByteArray>(600).also { lines ->
    for (status in HttpStatusCode.allStatusCodes) {
        if (status.value in lines.indices) {
            lines[status.value] = "HTTP/1.1 ${status.value} ${status.description}\r\n".encodeToByteArray()
        }
    }
}

private val statusDescriptions = arrayOfNulls<String>(600).also { descriptions ->
    for (status in HttpStatusCode.allStatusCodes) {
        if (status.value in descriptions.indices) {
            descriptions[status.value] = status.description
        }
    }
}

internal fun statusLine(status: HttpStatusCode): ByteArray {
    val code = status.value
    if (code in statusLines.indices && statusDescriptions[code] == status.description) {
        statusLines[code]?.let { return it }
    }

    return "HTTP/1.1 $code ${status.description}\r\n".encodeToByteArray()
}

// A header's characters, one byte each: Ktor has checked them already (no CR, LF or other control).
private fun putAscii(out: ByteArray, at: Int, s: String): Int {
    var p = at
    for (c in s) {
        out[p++] = c.code.toByte()
    }

    return p
}

/** The index of the first CR of the first CRLFCRLF in `[from, to)`, or -1. */
internal fun indexOfCrlfCrlf(a: ByteArray, from: Int, to: Int): Int {
    var i = from
    while (i + 3 < to) {
        if (a[i + 3] != LF) {
            // the LF that would end a CRLFCRLF starting at i, i + 1 or i + 2 is not there: skip past it
            i += if (a[i + 3] == CR) 1 else 4
            continue
        }

        if (a[i] == CR && a[i + 1] == LF && a[i + 2] == CR) {
            return i
        }

        i++
    }

    return -1
}

private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

/** Header [i]'s value equals [lower], compared case-insensitively. */
internal fun RequestHead.valueEquals(bytes: ByteArray, i: Int, lower: ByteArray): Boolean {
    val start = headerValueStart(i)
    var length = headerValueLength(i)
    while (length > 0 &&
        (bytes[start + length - 1] == ' '.code.toByte() || bytes[start + length - 1] == '\t'.code.toByte())
    ) {
        length--
    }

    return length == lower.size && asciiEqualsIgnoreCase(bytes, start, lower)
}

// Header [i]'s value, a comma-separated list, holds [lower] as one of its tokens, compared case-insensitively.
internal fun RequestHead.hasToken(bytes: ByteArray, i: Int, lower: ByteArray): Boolean {
    var p = headerValueStart(i)
    val end = p + headerValueLength(i)
    while (p < end) {
        while (p < end &&
            (bytes[p] == ' '.code.toByte() || bytes[p] == '\t'.code.toByte() || bytes[p] == ','.code.toByte())
        ) {
            p++
        }

        var tokenEnd = p
        while (tokenEnd < end && bytes[tokenEnd] != ','.code.toByte()) {
            tokenEnd++
        }

        var trimmed = tokenEnd
        while (trimmed > p && (bytes[trimmed - 1] == ' '.code.toByte() || bytes[trimmed - 1] == '\t'.code.toByte())) {
            trimmed--
        }

        if (trimmed - p == lower.size && asciiEqualsIgnoreCase(bytes, p, lower)) {
            return true
        }

        p = tokenEnd
    }

    return false
}

/** Header names [a] and [b] are the same: tokens, so ASCII, compared without Unicode case folding. */
internal fun sameName(a: String, b: String): Boolean {
    if (a.length != b.length) {
        return false
    }

    for (i in a.indices) {
        var x = a[i].code
        var y = b[i].code
        if (x in 'A'.code..'Z'.code) {
            x += 32
        }

        if (y in 'A'.code..'Z'.code) {
            y += 32
        }

        if (x != y) {
            return false
        }
    }

    return true
}

internal fun asciiEqualsIgnoreCase(bytes: ByteArray, at: Int, lower: ByteArray): Boolean {
    for (i in lower.indices) {
        var c = bytes[at + i].toInt()
        if (c in 'A'.code..'Z'.code) {
            c += 32
        }

        if (c != lower[i].toInt()) {
            return false
        }
    }

    return true
}
