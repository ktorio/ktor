package io.ktor.server.application

/**
 * Hot reload is only supported on JVM
 */
internal actual fun defaultWatchPaths(): List<String> = emptyList()
