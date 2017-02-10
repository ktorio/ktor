package org.jetbrains.ktor.host

import java.util.concurrent.*

interface ApplicationHostStartable {
    fun start(wait: Boolean = false)
    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit)
}