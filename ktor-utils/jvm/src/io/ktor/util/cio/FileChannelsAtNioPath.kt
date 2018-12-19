package io.ktor.util.cio

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import java.nio.file.*

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code
 */
@KtorExperimentalAPI
fun Path.readChannel(start: Long, endInclusive: Long): ByteReadChannel = toFile().readChannel(start, endInclusive)

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code
 */
@KtorExperimentalAPI
fun Path.readChannel(): ByteReadChannel = toFile().readChannel()
