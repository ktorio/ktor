/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth.ldap

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.ldap.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.io.*
import java.net.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.*

class LdapEmptyPasswordProofTest {
    @Test
    fun `permissive LDAP anonymous bind does not authenticate a named user without a password`() {
        PermissiveLdapServer().use { ldapServer ->
            testApplication {
                install(Authentication) {
                    basic {
                        validate { credential ->
                            ldapAuthenticate(credential, ldapServer.url, "uid=%s,ou=system")
                        }
                    }
                }
                routing {
                    authenticate {
                        get("/") { call.respondText(call.principal<UserIdPrincipal>()!!.name) }
                    }
                }

                val valid = client.get("/") { basicAuth("admin", "secret") }
                assertEquals(HttpStatusCode.OK, valid.status)
                assertEquals("admin", valid.bodyAsText())

                val invalid = client.get("/") { basicAuth("admin", "wrong") }
                assertEquals(HttpStatusCode.Unauthorized, invalid.status)

                val anonymous = client.get("/") { basicAuth("admin", "") }
                assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
            }
            assertEquals(listOf("secret", "wrong"), ldapServer.passwords)
        }
    }

    // Simulates an LDAP server that accepts RFC 4513 unauthenticated simple binds as anonymous.
    private class PermissiveLdapServer : Closeable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val failure = AtomicReference<Throwable?>()
        private val seenPasswords = mutableListOf<String>()
        val url: String = "ldap://127.0.0.1:${socket.localPort}"
        val passwords: List<String> get() = synchronized(seenPasswords) { seenPasswords.toList() }

        private val worker = thread(name = "permissive-ldap-test", isDaemon = true) {
            try {
                repeat(3) {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val packet = client.getInputStream().readField(0x30).inputStream()
                        val messageId = packet.readField(0x02).single()
                        val bind = packet.readField(0x60).inputStream()
                        assertContentEquals(byteArrayOf(3), bind.readField(0x02))
                        bind.readField(0x04) // Distinguished name; an unauthenticated bind does not verify it.
                        val password = bind.readField(0x80).toString(Charsets.UTF_8)
                        synchronized(seenPasswords) { seenPasswords.add(password) }

                        val resultCode: Byte = if (password == "secret" || password.isEmpty()) 0 else 49
                        val response = byteArrayOf(
                            0x30, 0x0c, 0x02, 0x01, messageId, 0x61, 0x07,
                            0x0a, 0x01, resultCode, 0x04, 0x00, 0x04, 0x00,
                        )
                        client.getOutputStream().write(response)
                    }
                }
            } catch (cause: Throwable) {
                if (!socket.isClosed) failure.set(cause)
            }
        }

        override fun close() {
            socket.close()
            worker.join(5_000)
            failure.get()?.let { throw AssertionError("LDAP proof server failed", it) }
        }

        private fun InputStream.readField(expectedTag: Int): ByteArray {
            assertEquals(expectedTag, read(), "Unexpected LDAP field tag")
            var length = read()
            require(length >= 0) { "Truncated LDAP field" }
            if (length and 0x80 != 0) {
                val lengthBytes = length and 0x7f
                require(lengthBytes in 1..4) { "Invalid LDAP field length" }
                length = 0
                repeat(lengthBytes) {
                    val next = read()
                    require(next >= 0) { "Truncated LDAP field length" }
                    length = (length shl 8) or next
                }
            }
            return ByteArray(length).also { DataInputStream(this).readFully(it) }
        }
    }
}
