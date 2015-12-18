package org.jetbrains.ktor.application

interface ApplicationHost {
    fun start()
    fun stop()
}