/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.cio

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.nio.file.*

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code
 */
public fun Path.readChannel(start: Long, endInclusive: Long): ByteReadChannel =
    toFile().readChannel(start, endInclusive)

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code
 */
public fun Path.readChannel(): ByteReadChannel = toFile().readChannel()
