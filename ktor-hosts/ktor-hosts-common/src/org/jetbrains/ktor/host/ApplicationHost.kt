package org.jetbrains.ktor.host

import java.util.concurrent.*

interface ApplicationHost {
    val environment : ApplicationHostEnvironment

    fun start(wait: Boolean = false): ApplicationHost
    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit)
}