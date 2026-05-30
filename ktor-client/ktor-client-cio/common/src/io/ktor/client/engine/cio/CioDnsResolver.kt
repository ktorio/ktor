/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.buildPacket
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.random.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Returns a [CIOEngineConfig.dnsResolver] that performs hostname lookups by speaking RFC 1035
 * directly over UDP using ktor-network. Fully non-blocking; supports a per-query timeout.
 *
 * Issues A and AAAA queries in parallel on a single UDP socket and returns the merged result with
 * IPv6 addresses listed first (per RFC 6724 / RFC 8305 destination preference). If only one
 * response arrives before [timeout], whatever was received is returned; if neither arrives,
 * [IOException] is thrown.
 *
 * Bypasses the OS name service entirely — it does not consult `/etc/hosts`, mDNS, or
 * NSS-configured local resolvers. Use [JvmDnsResolver] when those need to keep working.
 *
 * Unlike Netty's `DnsNameResolver`, this implementation does not auto-discover the system
 * resolver (`/etc/resolv.conf`, JNDI, Windows registry); [server] must be passed explicitly.
 *
 * ```kotlin
 * HttpClient(CIO) {
 *     engine {
 *         dnsResolver = CioDnsResolver(server = "1.1.1.1", timeout = 3.seconds)
 *     }
 * }
 * ```
 *
 * Limitations of this minimal implementation:
 * - A / AAAA records only; CNAME chains are not followed
 * - No TCP fallback when the response has the TC (truncation) flag set
 * - No retry; wrap with your own retry policy if needed
 * - UDP is not supported on JS/Wasm; calling there will throw
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CioDnsResolver)
 */
@Suppress("FunctionName")
public fun CioDnsResolver(
    server: String,
    port: Int = 53,
    timeout: Duration = 5.seconds,
): suspend (hostname: String) -> List<String> = { hostname ->
    require(hostname.isNotEmpty()) { "hostname must not be empty" }

    val queryIdA = Random.nextInt(0, 0x10000)
    val queryIdAaaa = (queryIdA + 1) and 0xFFFF
    val resolverAddress = InetSocketAddress(server, port)

    SelectorManager().use { selector ->
        aSocket(selector).udp().bind().use { socket ->
            socket.send(Datagram(buildDnsQuery(queryIdA, hostname, DNS_TYPE_A), resolverAddress))
            socket.send(Datagram(buildDnsQuery(queryIdAaaa, hostname, DNS_TYPE_AAAA), resolverAddress))

            val ipv4 = mutableListOf<String>()
            val ipv6 = mutableListOf<String>()
            var receivedA = false
            var receivedAaaa = false

            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (!(receivedA && receivedAaaa)) {
                val remaining = deadline - TimeSource.Monotonic.markNow()
                if (!remaining.isPositive()) break
                val datagram = withTimeoutOrNull(remaining) { socket.incoming.receive() } ?: break
                val response = try {
                    parseDnsResponse(datagram.packet)
                } catch (_: EOFException) {
                    null // truncated/malformed datagram from any source on this socket
                } ?: continue
                when (response.queryId) {
                    queryIdA -> {
                        ipv4 += response.addresses
                        receivedA = true
                    }

                    queryIdAaaa -> {
                        ipv6 += response.addresses
                        receivedAaaa = true
                    }
                }
            }

            if (!receivedA && !receivedAaaa) {
                throw IOException("DNS resolution of '$hostname' via $server timed out after $timeout")
            }

            ipv6 + ipv4
        }
    }
}

private data class DnsResponse(val queryId: Int, val addresses: List<String>)

private fun buildDnsQuery(id: Int, hostname: String, qtype: Short): Source = buildPacket {
    writeShort(id.toShort()) // ID
    writeShort(0x0100.toShort()) // Flags: standard query, RD=1
    writeShort(1) // QDCOUNT
    writeShort(0) // ANCOUNT
    writeShort(0) // NSCOUNT
    writeShort(0) // ARCOUNT
    // RFC 1035 §3.1: an FQDN may carry a trailing dot for the root label; strip it so
    // split('.') doesn't produce an empty trailing label that fails the length check.
    for (label in hostname.removeSuffix(".").split('.')) {
        val bytes = label.encodeToByteArray()
        require(bytes.size in 1..63) { "Invalid DNS label '$label' in '$hostname'" }
        writeByte(bytes.size.toByte())
        write(bytes)
    }
    writeByte(0) // QNAME terminator
    writeShort(qtype) // QTYPE
    writeShort(DNS_CLASS_IN) // QCLASS
}

/**
 * Parses [source] into the response's query ID and the list of A/AAAA addresses contained in
 * the answer section. Returns `null` for malformed packets so the caller can keep listening
 * (e.g. stale datagrams from a previous query).
 */
private fun parseDnsResponse(source: Source): DnsResponse? {
    if (source.exhausted()) return null
    val id = source.readShort().toInt() and 0xFFFF
    val flags = source.readShort().toInt() and 0xFFFF
    val rcode = flags and 0xF
    val qdCount = source.readShort().toInt() and 0xFFFF
    val anCount = source.readShort().toInt() and 0xFFFF
    source.readShort() // NSCOUNT
    source.readShort() // ARCOUNT

    repeat(qdCount) {
        skipDnsName(source)
        source.readShort() // QTYPE
        source.readShort() // QCLASS
    }

    if (rcode != 0) return DnsResponse(id, emptyList())

    val addresses = mutableListOf<String>()
    repeat(anCount) {
        skipDnsName(source)
        val type = source.readShort().toInt() and 0xFFFF
        val cls = source.readShort().toInt() and 0xFFFF
        source.readInt() // TTL
        val rdLength = source.readShort().toInt() and 0xFFFF
        when {
            cls != DNS_CLASS_IN.toInt() -> source.skip(rdLength.toLong())
            type == DNS_TYPE_A.toInt() && rdLength == 4 -> addresses += readIpv4(source)
            type == DNS_TYPE_AAAA.toInt() && rdLength == 16 -> addresses += readIpv6(source)
            else -> source.skip(rdLength.toLong())
        }
    }
    return DnsResponse(id, addresses)
}

private fun readIpv4(source: Source): String {
    val a = source.readByte().toInt() and 0xFF
    val b = source.readByte().toInt() and 0xFF
    val c = source.readByte().toInt() and 0xFF
    val d = source.readByte().toInt() and 0xFF
    return "$a.$b.$c.$d"
}

private fun readIpv6(source: Source): String {
    val groups = IntArray(8) {
        ((source.readByte().toInt() and 0xFF) shl 8) or (source.readByte().toInt() and 0xFF)
    }
    return formatIpv6(groups)
}

/**
 * Formats an IPv6 address with the longest run of zero groups collapsed to `::` per RFC 5952.
 * Single zero groups are not collapsed; runs of length 2 or more are.
 */
internal fun formatIpv6(groups: IntArray): String {
    require(groups.size == 8) { "IPv6 address must have 8 groups, got ${groups.size}" }

    var bestStart = -1
    var bestLength = 0
    var currentStart = -1
    var currentLength = 0
    for (i in groups.indices) {
        if (groups[i] == 0) {
            if (currentStart == -1) currentStart = i
            currentLength++
            if (currentLength > bestLength) {
                bestStart = currentStart
                bestLength = currentLength
            }
        } else {
            currentStart = -1
            currentLength = 0
        }
    }
    if (bestLength < 2) {
        bestStart = -1
        bestLength = 0
    }

    return buildString {
        var i = 0
        while (i < 8) {
            if (i == bestStart) {
                append("::")
                i += bestLength
                continue
            }
            if (i > 0 && i != bestStart + bestLength) append(':')
            append(groups[i].toString(16))
            i++
        }
    }
}

private fun skipDnsName(source: Source) {
    while (true) {
        val len = source.readByte().toInt() and 0xFF
        when {
            len == 0 -> return
            len and DNS_POINTER_MASK == DNS_POINTER_MASK -> {
                source.readByte() // 2-byte pointer; first byte already consumed
                return
            }

            else -> source.skip(len.toLong())
        }
    }
}

private const val DNS_TYPE_A: Short = 1
private const val DNS_TYPE_AAAA: Short = 28
private const val DNS_CLASS_IN: Short = 1
private const val DNS_POINTER_MASK = 0xC0
