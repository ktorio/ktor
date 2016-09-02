package org.jetbrains.ktor.host

interface ApplicationHostStartable {
    fun start(wait: Boolean = false)
    fun stop()
}