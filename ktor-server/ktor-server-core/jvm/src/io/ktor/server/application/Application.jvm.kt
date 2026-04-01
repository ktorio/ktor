package io.ktor.server.application

import io.ktor.server.engine.WORKING_DIRECTORY_PATH

internal actual fun defaultWatchPaths(): List<String> = listOf(WORKING_DIRECTORY_PATH)
