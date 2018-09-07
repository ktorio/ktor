package io.ktor.network.tls

import io.ktor.network.sockets.*
import kotlinx.coroutines.io.*
import javax.net.ssl.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS.
 */
suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    trustManager: X509TrustManager? = null,
    randomAlgorithm: String = "NativePRNGNonBlocking",
    cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites,
    serverName: String? = null
): Socket {
    val reader = openReadChannel()
    val writer = openWriteChannel()

    val session = try {
        TLSClientSession(
            reader, writer, trustManager, randomAlgorithm, cipherSuites, serverName, coroutineContext
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
