package org.jetbrains.ktor.host

interface ApplicationHost {
    fun start()
    fun stop()
}