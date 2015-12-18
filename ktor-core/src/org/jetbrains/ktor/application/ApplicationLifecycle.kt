package org.jetbrains.ktor.application

interface ApplicationLifecycle {
    val application : Application
    fun dispose()
}