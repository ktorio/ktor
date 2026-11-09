/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.files.IoUringEventLoopRing
import io.netty.channel.EventLoopGroup
import io.netty.channel.IoEventLoop
import io.netty.channel.IoRegistration
import io.netty.channel.epoll.EpollIoHandle
import io.netty.channel.epoll.EpollIoOps
import io.netty.channel.unix.FileDescriptor

@OptIn(InternalAPI::class)
internal class NettyIoUringFileRings private constructor(
    private val registrations: List<RegisteredRing>,
) {
    fun close() {
        registrations.forEach(RegisteredRing::close)
    }

    private class RegisteredRing(
        private val eventLoop: IoEventLoop,
        private val registration: IoRegistration,
        private val handle: IoUringEventFdHandle,
    ) {
        fun close() {
            val closeAction = {
                registration.cancel()
                handle.close()
            }
            if (eventLoop.inEventLoop()) {
                closeAction()
            } else {
                runCatching { eventLoop.submit { closeAction() }.syncUninterruptibly() }
            }
        }
    }

    private class IoUringEventFdHandle(eventFd: Int) : EpollIoHandle {
        private val fileDescriptor = FileDescriptor(eventFd)

        override fun fd(): FileDescriptor = fileDescriptor

        override fun handle(registration: IoRegistration, ioEvent: io.netty.channel.IoEvent) {
            if (!IoUringEventLoopRing.processCompletions()) {
                registration.cancel()
                IoUringEventLoopRing.close()
            }
        }

        override fun close() {
            IoUringEventLoopRing.close()
        }
    }

    companion object {
        fun install(vararg groups: EventLoopGroup): NettyIoUringFileRings {
            val registrations = mutableListOf<RegisteredRing>()
            val uniqueGroups = mutableListOf<EventLoopGroup>()
            groups.forEach { group ->
                if (uniqueGroups.none { it === group }) uniqueGroups += group
            }

            uniqueGroups.forEach { group ->
                group.forEach executorLoop@{ executor ->
                    val eventLoop = executor as? IoEventLoop ?: return@executorLoop
                    if (!eventLoop.isCompatible(EpollIoHandle::class.java)) return@executorLoop

                    val registered = runCatching {
                        if (eventLoop.inEventLoop()) {
                            registerRing(eventLoop)
                        } else {
                            eventLoop.submit<RegisteredRing?> { registerRing(eventLoop) }.syncUninterruptibly().getNow()
                        }
                    }.getOrNull()

                    if (registered != null) registrations += registered
                }
            }
            return NettyIoUringFileRings(registrations)
        }

        private fun registerRing(eventLoop: IoEventLoop): RegisteredRing? {
            var handle: IoUringEventFdHandle? = null
            var registration: IoRegistration? = null
            try {
                val eventFd = IoUringEventLoopRing.createForCurrentThread()
                if (eventFd < 0) return null

                val eventHandle = IoUringEventFdHandle(eventFd)
                handle = eventHandle
                val ioRegistration =
                    eventLoop.register(eventHandle).syncUninterruptibly().getNow()
                        ?: error("Failed to register io_uring eventfd")
                registration = ioRegistration
                check(ioRegistration.submit(EpollIoOps.EPOLLIN) >= 0) {
                    "Failed to arm io_uring eventfd"
                }
                return RegisteredRing(eventLoop, ioRegistration, eventHandle)
            } catch (_: Throwable) {
                registration?.cancel()
                if (handle != null) {
                    handle.close()
                } else {
                    IoUringEventLoopRing.close()
                }
                return null
            }
        }
    }
}
