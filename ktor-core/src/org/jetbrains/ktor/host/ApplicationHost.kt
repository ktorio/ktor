package org.jetbrains.ktor.host

import java.util.concurrent.*

interface ApplicationHost {
    val hostConfig: ApplicationHostConfig
    val executor: Executor

    fun start(wait: Boolean = false)
    fun stop()
}