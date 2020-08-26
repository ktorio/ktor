package io.ktor.client.tests 

import io.ktor.network.tls.extensions.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import java.security.Principal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 
from copied TLSClientHandshake.handleCertificatesAndKeys().TLSHandshakeType.CertificateRequest
*/
internal class CIOTLSHandshkeCertificateRequestAuthoritiesSizeTest {
    // bytes of CA certificates are mapped to 1
    val packet = ByteReadPacket("""3 1 2 64 0 24 4 1 4 2 4 3 5 1 5 2 5 3 6 1 6 2 6 3 2 1 2 2 2 3 1 23 0 91 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 0 91 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 0 91 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1""".split(" ")
            .map {
                it.toInt()
            }.map {
                it.toByte()
            }.toByteArray())

    @Test
    @InternalAPI
    fun failed() {
        assertFailsWith<java.io.EOFException> {
            val typeCount = packet.readByte().toInt() and 0xFF
            val types = packet.readBytes(typeCount)

            val hashAndSignCount = packet.readShort().toInt() and 0xFFFF
            val hashAndSign = mutableListOf<HashAndSign>()


            repeat(hashAndSignCount / 2) {
                val hash = packet.readByte()
                val sign = packet.readByte()
                hashAndSign += HashAndSign.byCode(hash, sign) ?: return@repeat
            }

            val authoritiesSize = packet.readShort().toInt() and 0xFFFF
            val authorities = mutableSetOf<Principal>()

            var position = 0
            while (position < authoritiesSize) {
                val size = packet.readShort().toInt() and 0xFFFF
                position += size

                val authority = packet.readBytes(size)
                assertEquals(91, authority.size)
                //authorities += X500Principal(authority)
            }

            //val certificateInfo = CertificateInfo(types, hashAndSign.toTypedArray(), authorities)
            check(packet.isEmpty)
        }
    }

    @Test
    @InternalAPI
    fun fixed() {
        val typeCount = packet.readByte().toInt() and 0xFF
        val types = packet.readBytes(typeCount)

        val hashAndSignCount = packet.readShort().toInt() and 0xFFFF
        val hashAndSign = mutableListOf<HashAndSign>()


        repeat(hashAndSignCount / 2) {
            val hash = packet.readByte()
            val sign = packet.readByte()
            hashAndSign += HashAndSign.byCode(hash, sign) ?: return@repeat
        }

        val authoritiesSize = packet.readShort().toInt() and 0xFFFF
        val authorities = mutableSetOf<Principal>()

        var position = 0
        while (position < authoritiesSize) {
            val size = packet.readShort().toInt() and 0xFFFF
            position += size + Short.SIZE_BYTES

            val authority = packet.readBytes(size)
            assertEquals(91, authority.size)
            // authorities += X500Principal(authority)
        }

        //val certificateInfo = CertificateInfo(types, hashAndSign.toTypedArray(), authorities)
        check(packet.isEmpty)
    }
}
