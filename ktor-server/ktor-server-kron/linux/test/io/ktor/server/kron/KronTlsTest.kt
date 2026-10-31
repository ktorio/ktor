/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kron.tls.TlsOptions
import platform.posix.AF_INET
import platform.posix.ENOTCONN
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.X_OK
import platform.posix.access
import platform.posix.close
import platform.posix.errno
import platform.posix.fread
import platform.posix.mkstemp
import platform.posix.pclose
import platform.posix.popen
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.unlink
import platform.posix.write
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// HTTPS on the engine's TLS connector, driven by curl: Ktor's own TLS suites build a JVM KeyStore. Each test runs on
// BoringSSL both ways and, where the kernel has the tls module, with kernel TX as well.
class KronTlsTest {
    @Test
    fun servesHttpsBesideHttp() = eachBackend { server ->
        val (plain, secure) = ports(server)
        assertEquals("hello", curl("http://127.0.0.1:$plain/hello")!!.text)
        assertEquals("hello", curl("-k", "https://127.0.0.1:$secure/hello")!!.text)
        assertEquals("https", curl("-k", "https://127.0.0.1:$secure/scheme")!!.text)
        assertEquals("http", curl("http://127.0.0.1:$plain/scheme")!!.text)
        assertEquals("1.1", curl("-k", "--tlsv1.3", "-o", "/dev/null", "-w", "%{http_version}", url(secure))!!.text)
    }

    @Test
    fun echoesBodiesByteForByte() = eachBackend { server ->
        val secure = ports(server).second
        for (size in listOf(0, 1, 1024, 10_240, 102_400)) {
            val body = Random(size).nextBytes(size)
            withFile(body) { path ->
                val sized = curl("-k", "--data-binary", "@$path", "https://127.0.0.1:$secure/echo")!!
                assertContentEquals(body, sized.bytes, "$size bytes, Content-Length")

                val chunked = curl(
                    "-k",
                    "-H",
                    "Transfer-Encoding: chunked",
                    "--data-binary",
                    "@$path",
                    "https://127.0.0.1:$secure/echo"
                )!!
                assertContentEquals(body, chunked.bytes, "$size bytes, chunked")
            }
        }
    }

    @Test
    fun aResponseSpanningManyRecords() = eachBackend { server ->
        val secure = ports(server).second
        assertContentEquals(large, curl("-k", "https://127.0.0.1:$secure/large")!!.bytes)
    }

    @Test
    fun keepsTheConnectionForMoreRequests() = eachBackend { server ->
        val secure = ports(server).second
        val result = curl("-k", url(secure), url(secure), url(secure), "-w", "%{num_connects}")!!
        assertEquals("hello1hello0hello0", result.text)
    }

    @Test
    fun aFailedHandshakeLeavesTheServerServing() = eachBackend { server ->
        val (plain, secure) = ports(server)
        assertNotEquals(0, curl("-k", "--tlsv1.1", "--tls-max", "1.1", url(secure))!!.status)
        assertNotEquals(0, curl("http://127.0.0.1:$secure/hello")!!.status)
        assertEquals("hello", curl("-k", url(secure))!!.text)
        assertEquals("hello", curl("http://127.0.0.1:$plain/hello")!!.text)
    }

    private val large = ByteArray(1024 * 1024) { (it * 31 + it / 7).toByte() }

    private fun module(): Application.() -> Unit = {
        routing {
            get("/hello") { call.respondText("hello") }
            get("/scheme") { call.respondText(call.request.local.scheme) }
            get("/large") { call.respondBytes(large, ContentType.Application.OctetStream) }
            post("/echo") { call.respondBytes(call.receive<ByteArray>(), ContentType.Application.OctetStream) }
        }
    }

    // The test once on BoringSSL both ways, and once with the kernel encrypting where it can; skipped without curl.
    private fun eachBackend(
        test: (EmbeddedServer<KronApplicationEngine, KronApplicationEngine.Configuration>) -> Unit
    ) {
        if (access(CURL, X_OK) != 0) {
            println("skipped: no $CURL")
            return
        }

        val backends = if (kernelTlsAvailable()) listOf(false, true) else listOf(false)
        for (kernelTx in backends) {
            val server = embeddedServer(Kron, configure = {
                connector { port = 0 }
                tlsConnector(0, TlsOptions(certificatePem = CERTIFICATE, keyPem = KEY, kernelTx = kernelTx))
                reactorCount = 1
            }, module = module()).start(wait = false)

            try {
                test(server)
            } catch (e: Throwable) {
                throw AssertionError("kernelTx = $kernelTx: ${e.message}", e)
            } finally {
                server.stop(0, 0)
            }
        }
    }

    private fun ports(server: EmbeddedServer<KronApplicationEngine, *>): Pair<Int, Int> = runBlocking {
        val connectors = server.engine.resolvedConnectors()
        val plain = connectors.single { it.type == ConnectorType.HTTP }
        val secure = connectors.single { it.type == ConnectorType.HTTPS }
        plain.port to secure.port
    }

    private fun url(port: Int) = "https://127.0.0.1:$port/hello"

    private class Result(val status: Int, val bytes: ByteArray) {
        val text: String get() = bytes.decodeToString()
    }

    // curl with args, its exit status and what it wrote to stdout
    private fun curl(vararg args: String): Result? {
        val quoted = (listOf(CURL, "-s", "--max-time", "10") + args).map { "'" + it.replace("'", "'\\''") + "'" }
        val command = quoted.joinToString(" ")
        val pipe = popen(command, "r") ?: return null
        var out = ByteArray(64 * 1024)
        var length = 0
        while (true) {
            if (length == out.size) {
                out = out.copyOf(out.size * 2)
            }

            val n = out.usePinned { fread(it.addressOf(length), 1u, (out.size - length).convert(), pipe) }.toInt()
            if (n <= 0) {
                break
            }

            length += n
        }

        val status = pclose(pipe)
        return Result((status shr 8) and 0xff, out.copyOf(length))
    }

    private fun withFile(bytes: ByteArray, block: (String) -> Unit) {
        val template = "/tmp/kron-tls-XXXXXX".encodeToByteArray() + 0
        val fd = mkstemp(template.refTo(0))
        check(fd >= 0) { "mkstemp failed" }
        val path = template.toKString()
        try {
            var written = 0
            while (written < bytes.size) {
                val n = bytes.usePinned { write(fd, it.addressOf(written), (bytes.size - written).convert()) }.toInt()
                check(n > 0) { "write failed" }
                written += n
            }

            close(fd)
            block(path)
        } finally {
            unlink(path)
        }
    }

    // As kron's own HttpArena entry asks: a socket that is not connected takes the tls ULP only where the module is in
    // (ENOTCONN), and refuses it with ENOENT where it is not.
    private fun kernelTlsAvailable(): Boolean {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) {
            return false
        }

        try {
            val ulp = "tls".encodeToByteArray()
            val rc = ulp.usePinned { setsockopt(fd, IPPROTO_TCP, TCP_ULP, it.addressOf(0), ulp.size.convert()) }
            return rc == 0 || errno == ENOTCONN
        } finally {
            close(fd)
        }
    }

    private companion object {
        const val CURL = "/usr/bin/curl"
        const val TCP_ULP = 31

        // A self-signed P-256 pair for localhost and 127.0.0.1, valid until 2126.
        val CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBmzCCAUGgAwIBAgIUXajU8d1ZXxf3mhBQL0EtGimxtvEwCgYIKoZIzj0EAwIw
            FDESMBAGA1UEAwwJbG9jYWxob3N0MCAXDTI2MTAzMDE2NDgwN1oYDzIxMjYxMDA2
            MTY0ODA3WjAUMRIwEAYDVQQDDAlsb2NhbGhvc3QwWTATBgcqhkjOPQIBBggqhkjO
            PQMBBwNCAASZG0OllmRyyAkhpGxOEPa/FiQC97cn22v2bIILtafuY4gBYeyGnfQZ
            9FxqsnZ8jPfnNNTuWixmouM38s0mOSV/o28wbTAdBgNVHQ4EFgQUV+cgB3hn5oFO
            yHw7OzhCS9KeaZUwHwYDVR0jBBgwFoAUV+cgB3hn5oFOyHw7OzhCS9KeaZUwDwYD
            VR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SHBH8AAAEwCgYIKoZI
            zj0EAwIDSAAwRQIhAOjh+JKUD1M0OV9PzSzxqA+P9ACiE4BeqwVJr7tMOha5AiBv
            Y/+5hcGcJwKp55ne9sOewSi+p1DaBJ2br1aKZrLYdw==
            -----END CERTIFICATE-----
        """.trimIndent()

        val KEY = """
            -----BEGIN PRIVATE KEY-----
            MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgxm323caUpwUGHVvl
            OI1vwz5MiYBQ7VbJlcHLH5157ayhRANCAASZG0OllmRyyAkhpGxOEPa/FiQC97cn
            22v2bIILtafuY4gBYeyGnfQZ9FxqsnZ8jPfnNNTuWixmouM38s0mOSV/
            -----END PRIVATE KEY-----
        """.trimIndent()
    }
}
