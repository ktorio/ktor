package org.jetbrains.ktor.host

interface ApplicationHost {
    val hostConfig: ApplicationHostConfig
    fun start(wait: Boolean = false)
    fun stop()
}