/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import org.slf4j.*
import java.net.*
import java.util.*
import kotlin.concurrent.*

internal object FreePorts {
    private const val CAPACITY = 20
    private const val CAPACITY_LOW = 10
    private const val UDP_ATTEMPTS = 10

    private val found = Collections.synchronizedSet(HashSet<Int>())
    private val free = Collections.synchronizedList(LinkedList<Int>())

    init {
        allocate(CAPACITY)
    }

    fun select(): Int {
        if (free.size < CAPACITY_LOW) {
            thread(name = "free-port-population") {
                allocate(CAPACITY - free.size)
            }
        }

        while (true) {
            try {
                return free.removeAt(0)
            } catch (expected: IndexOutOfBoundsException) {
                // may happen if concurrently removed
                allocate(CAPACITY)
            }
        }
    }

    /**
     * Selects a port that is free for both TCP and UDP.
     *
     * [select] probes with TCP sockets only, but an HTTP/3 server binds UDP on the same port as its
     * TCP SSL connector. A TCP-free port is not necessarily UDP-free: on Windows the dynamic range
     * overlaps Hyper-V/WSL excluded UDP ranges, so the UDP bind fails while the TCP one succeeds.
     */
    fun selectTcpAndUdp(): Int {
        repeat(UDP_ATTEMPTS) {
            val port = select()
            if (checkFreeUdpPort(port)) return port

            // Deliberately not recycled: the port is TCP-free, so recycle() would hand it back out.
            log("Port $port is not free for UDP, selecting another one")
        }

        error("Unable to find a port free for both TCP and UDP after $UDP_ATTEMPTS attempts")
    }

    fun recycle(port: Int) {
        if (port in found && checkFreePort(port)) {
            free.add(port)
        }
    }

    private fun allocate(count: Int) {
        if (count <= 0) return
        val sockets = ArrayList<ServerSocket>()

        try {
            for (repeat in 1..count) {
                try {
                    val socket = ServerSocket(0, 1)
                    sockets.add(socket)
                } catch (ignore: Throwable) {
                    log("Waiting for free ports")
                    Thread.sleep(1000)
                }
            }
        } finally {
            sockets.removeAll {
                try {
                    it.close()
                    !found.add(it.localPort)
                } catch (ignore: Throwable) {
                    true
                }
            }

            log("Waiting for ports cleanup")
            Thread.sleep(1000)

            sockets.forEach {
                free.add(it.localPort)
            }
        }
    }

    private fun checkFreePort(port: Int): Boolean {
        try {
            ServerSocket(port).close()
            return true
        } catch (unableToBind: Throwable) {
            return false
        }
    }

    private fun checkFreeUdpPort(port: Int): Boolean {
        try {
            DatagramSocket(port).close()
            return true
        } catch (unableToBind: Throwable) {
            return false
        }
    }

    private fun log(message: String) {
        LoggerFactory.getLogger(FreePorts::class.java).info(message)
    }
}
