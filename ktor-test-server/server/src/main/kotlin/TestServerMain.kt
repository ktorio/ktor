/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import kotlinx.coroutines.*
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    ProcessHandle.current().parent().ifPresent { parent ->
        parent.onExit().thenRun { exitProcess(0) }
    }

    startServer(CoroutineScope(Dispatchers.Default), verbose = "--verbose" in args)
    System.getProperty("ktor.testServer.readyMarker")?.let(::println)
    awaitCancellation()
}
