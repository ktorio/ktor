/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.io.IOException

public typealias CancellationException = kotlinx.coroutines.CancellationException

/**
 * Exception wrapper for causes of byte channel closures.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ClosedByteChannelException)
 */
public open class ClosedByteChannelException(cause: Throwable? = null) : IOException(cause?.message, cause)

/**
 * Exception thrown when attempting to write to a closed byte channel.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ClosedWriteChannelException)
 */
public class ClosedWriteChannelException(cause: Throwable? = null) : ClosedByteChannelException(cause)

/**
 * Exception thrown when attempting to read from a closed byte channel.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ClosedReadChannelException)
 */
public class ClosedReadChannelException(cause: Throwable? = null) : ClosedByteChannelException(cause)
