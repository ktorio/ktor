/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.test

import org.slf4j.*
import java.net.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.concurrent.*

public object FreePorts {
    private const val CAPACITY = 20
    private const val CAPACITY_LOW = 10

    private val found = Collections.synchronizedSet(HashSet<Int>())
    private val free = Collections.synchronizedList(LinkedList<Int>())

    init {
        allocate(CAPACITY)
    }

    public fun select(): Int {
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

    public fun recycle(port: Int) {
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
        return try {
            ServerSocket(port).close()
            true
        } catch (unableToBind: Throwable) {
            false
        }
    }

    private fun log(message: String) {
        LoggerFactory.getLogger(FreePorts::class.java).info(message)
    }
}
