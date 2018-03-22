package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.io.*
import javax.net.ssl.*
import kotlin.coroutines.experimental.*

suspend fun ReadWriteSocket.tls(
        trustManager: X509TrustManager? = null,
        serverName: String? = null,
        coroutineContext: CoroutineContext = ioCoroutineDispatcher
): ReadWriteSocket {
    val session = TLSClientSession(openReadChannel(), openWriteChannel(), trustManager, serverName, coroutineContext)
    val socket = ReadWriteImpl(session, this)

    try {
        session.negotiate()
    } catch (t: Throwable) {
        socket.close()
        session.output.close(t)
        throw t
    }

    return socket
}

suspend fun Socket.tls(
        trustManager: X509TrustManager? = null,
        randomAlgorithm: String = "NativePRNGNonBlocking",
        serverName: String? = null,
        coroutineContext: CoroutineContext = ioCoroutineDispatcher
): Socket {
    val session = TLSClientSession(
            openReadChannel(), openWriteChannel(),
            trustManager, serverName, coroutineContext, randomAlgorithm
    )

    val socket = TLSSocketImpl(session, this)

    try {
        session.negotiate()
    } catch (t: Throwable) {
        socket.close()
        session.output.close(t)
        throw t
    }

    return socket
}

private class TLSSocketImpl(val session: TLSClientSession, val delegate: Socket) : Socket by delegate {
    override fun attachForReading(channel: ByteChannel): WriterJob = session.attachForReading(channel)
    override fun attachForWriting(channel: ByteChannel): ReaderJob = session.attachForWriting(channel)
}

private class ReadWriteImpl(val session: TLSClientSession, val delegate: ReadWriteSocket) : ReadWriteSocket by delegate {
    override fun attachForReading(channel: ByteChannel): WriterJob = session.attachForReading(channel)
    override fun attachForWriting(channel: ByteChannel): ReaderJob = session.attachForWriting(channel)
}
