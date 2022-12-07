/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import org.gradle.api.*
import test.server.*
import test.server.startServer
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

class TestServerPlugin : Plugin<Project> {
    private var server: Closeable? = null
    private val activeTasks = AtomicInteger(0)

    fun start() {
        val count = activeTasks.incrementAndGet()
        if (count == 1) {
            server = startServer()
        }
    }

    fun stop() {
        val count = activeTasks.decrementAndGet()
        if (count == 0) {
            server!!.close()
            server = null
        }
    }

    override fun apply(target: Project) {
        target.configure(target.tasks, object : Action<Task> {
            override fun execute(task: Task) {
                task.doFirst(object : Action<Task> {
                    override fun execute(t: Task) {
                        start()
                    }
                })
            }
        })
    }
}
