package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.io.*
import javax.net.ssl.*
import kotlin.coroutines.experimental.*

suspend fun Socket.tls(
    trustManager: X509TrustManager? = null,
    randomAlgorithm: String = "NativePRNGNonBlocking",
    cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites,
    serverName: String? = null,
    coroutineContext: CoroutineContext = ioCoroutineDispatcher
): Socket {
    val reader = openReadChannel()
    val writer = openWriteChannel()

    val session = try {
        TLSClientSession(
            reader, writer, coroutineContext,
            trustManager, randomAlgorithm, cipherSuites, serverName
        ).also { it.start() }
    } catch (cause: Throwable) {
        reader.cancel(cause)
        writer.close(cause)
        close()
        throw cause
    }

    return TLSSocketImpl(session, this)
}

private class TLSSocketImpl(val session: TLSClientSession, val delegate: Socket) : Socket by delegate {
    override fun attachForReading(channel: ByteChannel): WriterJob = session.attachForReading(channel)
    override fun attachForWriting(channel: ByteChannel): ReaderJob = session.attachForWriting(channel)
}
