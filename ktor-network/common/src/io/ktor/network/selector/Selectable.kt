/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.*

/**
 * A selectable entity with selectable NIO [channel], [interestedOps] subscriptions.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.Selectable)
 */
public expect interface Selectable

/**
 * A [CancellationException] thrown when an operation is cancelled due to the underlying channel being closed.
 *
 * This exception is used to signal that a suspended I/O operation cannot complete
 * because the channel it was waiting on has been closed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.ClosedChannelCancellationException)
 */
public class ClosedChannelCancellationException : CancellationException("Closed channel.")
