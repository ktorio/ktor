/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import java.io.*
import java.lang.reflect.*
import java.nio.channels.*

private const val SO_REUSEPORT = "SO_REUSEPORT"

// we invoke JDK7 specific api using reflection
// all used API is public so it still works on JDK9+
internal object SocketOptionsPlatformCapabilities {
    private val standardSocketOptions: Map<String, Field> = try {
        Class.forName("java.net.StandardSocketOptions")
            ?.fields
            ?.filter {
                it.modifiers.let { modifiers ->
                    Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers)
                }
            }
            ?.associateBy { it.name }
            ?: emptyMap()
    } catch (_: Throwable) {
        emptyMap()
    }

    private val channelSetOption: Method? = try {
        val socketOptionType = Class.forName("java.net.SocketOption")!!
        val socketChannelClass = Class.forName("java.nio.channels.SocketChannel")

        socketChannelClass.methods.firstOrNull { method ->
            method.modifiers.let { modifiers ->
                Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
            } && method.name == "setOption" &&
                method.parameterTypes.size == 2 &&
                method.returnType == socketChannelClass &&
                method.parameterTypes[0] == socketOptionType &&
                method.parameterTypes[1] == Object::class.java
        }
    } catch (_: Throwable) {
        null
    }

    private val serverChannelSetOption: Method? = try {
        val socketOptionType = Class.forName("java.net.SocketOption")!!
        val socketChannelClass = Class.forName("java.nio.channels.ServerSocketChannel")

        socketChannelClass.methods.firstOrNull { method ->
            method.modifiers.let { modifiers ->
                Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
            } &&
                method.name == "setOption" &&
                method.parameterTypes.size == 2 &&
                method.returnType == socketChannelClass &&
                method.parameterTypes[0] == socketOptionType &&
                method.parameterTypes[1] == Object::class.java
        }
    } catch (_: Throwable) {
        null
    }

    private val datagramSetOption: Method? = try {
        val socketOptionType = Class.forName("java.net.SocketOption")!!
        val socketChannelClass = Class.forName("java.nio.channels.DatagramChannel")

        socketChannelClass.methods.firstOrNull { method ->
            method.modifiers.let { modifiers ->
                Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
            } &&
                method.name == "setOption" &&
                method.parameterTypes.size == 2 &&
                method.returnType == socketChannelClass &&
                method.parameterTypes[0] == socketOptionType &&
                method.parameterTypes[1] == Object::class.java
        }
    } catch (_: Throwable) {
        null
    }

    public fun setReusePort(channel: SocketChannel) {
        val option = socketOption(SO_REUSEPORT)
        channelSetOption!!.invoke(channel, option, true)
    }

    public fun setReusePort(channel: ServerSocketChannel) {
        val option = socketOption(SO_REUSEPORT)
        serverChannelSetOption!!.invoke(channel, option, true)
    }

    public fun setReusePort(channel: DatagramChannel) {
        val option = socketOption(SO_REUSEPORT)
        datagramSetOption!!.invoke(channel, option, true)
    }

    private fun socketOption(name: String) =
        standardSocketOptions[name]?.get(null) ?: throw IOException("Socket option $name is not supported")
}

internal fun SelectableChannel.nonBlocking() {
    configureBlocking(false)
}

internal fun SelectableChannel.assignOptions(options: SocketOptions) {
    if (this is SocketChannel) {
        val socket = socket()!!

        if (options.typeOfService != TypeOfService.UNDEFINED) {
            socket.trafficClass = options.typeOfService.intValue
        }

        socket.reuseAddress = options.reuseAddress

        if (options.reusePort) {
            SocketOptionsPlatformCapabilities.setReusePort(this)
        }

        if (options is SocketOptions.PeerSocketOptions) {
            options.receiveBufferSize.takeIf { it > 0 }?.let { socket.receiveBufferSize = it }
            options.sendBufferSize.takeIf { it > 0 }?.let { socket.sendBufferSize = it }
        }
        if (options is SocketOptions.TCPClientSocketOptions) {
            options.lingerSeconds.takeIf { it >= 0 }?.let { socket.setSoLinger(true, it) }
            options.keepAlive?.let { socket.keepAlive = it }
            socket.tcpNoDelay = options.noDelay
        }
    }
    if (this is ServerSocketChannel) {
        val socket = socket()!!

        if (options.reuseAddress) {
            socket.reuseAddress = true
        }
        if (options.reusePort) {
            SocketOptionsPlatformCapabilities.setReusePort(this)
        }
    }
    if (this is DatagramChannel) {
        val socket = socket()!!

        if (options.typeOfService != TypeOfService.UNDEFINED) {
            socket.trafficClass = options.typeOfService.intValue
        }

        if (options.reuseAddress) {
            socket.reuseAddress = true
        }

        if (options.reusePort) {
            SocketOptionsPlatformCapabilities.setReusePort(this)
        }

        if (options is SocketOptions.UDPSocketOptions) {
            socket.broadcast = options.broadcast
        }
        if (options is SocketOptions.PeerSocketOptions) {
            options.receiveBufferSize.takeIf { it > 0 }?.let { socket.receiveBufferSize = it }
            options.sendBufferSize.takeIf { it > 0 }?.let { socket.sendBufferSize = it }
        }
    }
}
