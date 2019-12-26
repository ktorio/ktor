package io.ktor.http.cio

import io.ktor.network.sockets.*
import java.net.*
import java.nio.channels.*

fun remoteHost(socket: AConnectedSocket) = remoteHost(socket.remoteAddress)

fun remoteHost(socketChannel: SocketChannel) = remoteHost(socketChannel.remoteAddress)



fun remoteHost(socketAddress: SocketAddress): CharSequence {
    return (socketAddress as? InetSocketAddress)?.address?.let { remoteHost(it) } ?: ""
}

fun remoteHost(address: InetAddress) = when (address) {
    is Inet4Address -> address.address.joinToString(".") {
        (it.toInt() and 0xFF).toString()
    }
    is Inet6Address -> address.address.toList().chunked(2).joinToString(":") { pair ->
        val first = pair.first().toInt() and 0xFF
        val last = pair.last().toInt() and 0xFF
        (first * 256 + last).toString(16)
    }
    else -> ""
}
