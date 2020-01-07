package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import org.junit.Test
import kotlin.test.*
import java.net.*

class RemoteHostTest {
    @Test
    fun testIPV6() {
        val address = Inet6Address.getByAddress(ByteArray(16) { 0.toByte() })
        assertEquals("0:0:0:0:0:0:0:0", remoteHost(address))
        "2001:db8:85a3:0:0:8a2e:370:7334".also {
            assertEquals(it, remoteHost(Inet6Address.getByName(it)))
        }
    }
    @Test
    fun testIPV4() {
        val address = Inet4Address.getByAddress(ByteArray(4) { 0.toByte() })
        assertEquals("0.0.0.0", remoteHost(address))
        "192.0.2.235".also {
            assertEquals(it, remoteHost(Inet4Address.getByName(it)))
        }
    }
    @Test
    fun testLoopback() {
        val address = InetAddress.getLoopbackAddress()
        assertEquals("127.0.0.1", remoteHost(address))
    }
}
