/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.collections.*
import platform.posix.*
import kotlin.native.concurrent.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val init = setSignalHandler()

@InternalAPI
public object ThreadInfo {
    private val threads = ConcurrentMap<Worker, pthread_t>(initialCapacity = 32)

    init {
        init
    }

    public fun registerCurrentThread() {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val thread = pthread_self()!!
        threads[Worker.current] = thread
    }

    public fun dropWorker(worker: Worker) {
        threads.remove(worker)
    }

    public fun getAllStackTraces(): List<WorkerStacktrace> {
        if (kotlin.native.Platform.osFamily == OsFamily.WINDOWS) return emptyList()

        val result = mutableListOf<WorkerStacktrace>()
        val removed = mutableSetOf<Worker>()
        for ((worker, thread) in threads.entries) {
            try {
                val name = worker.name
                val stack = collectStack(thread)
                result += WorkerStacktrace(name, stack)
            } catch (_: Throwable) {
                removed.add(worker)
            }
        }

        removed.forEach {
            threads.remove(it)
        }

        return result
    }

    public fun printAllStackTraces() {
        getAllStackTraces().forEach {
            println(it.worker)
            it.stacktrace.forEach {
                println("\tat $it")
            }
        }
    }

    public fun stopAllWorkers() {
        for (worker in threads.keys) {
            if (worker == Worker.current) continue
            worker.requestTermination(processScheduledJobs = false)
        }

        threads.clear()
        registerCurrentThread()
    }
}

@InternalAPI
public class WorkerStacktrace(
    public val worker: String,
    public val stacktrace: List<String>
)

internal expect fun collectStack(thread: pthread_t): List<String>

internal expect fun setSignalHandler()
