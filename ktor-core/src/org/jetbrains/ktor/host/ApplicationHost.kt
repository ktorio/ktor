package org.jetbrains.ktor.host

interface ApplicationHost {
    val hostConfig: ApplicationHostConfig

    fun start()
    fun stop()
}