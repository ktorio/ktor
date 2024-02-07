/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.errors.*

/**
 * Base exception for any exceptions thrown from processing TLS sockets.
 */
public sealed class TLSException(message: String, cause: Throwable?) : IOException(message, cause)

/**
 * Connection was prematurely closed during TLS negotiation.
 */
public class TLSClosedChannelException(cause: Throwable? = null) : TLSException("Negotiation failed due to EOS", cause)

/**
 * Timed out waiting for peer during TLS negotiation.
 */
public class TLSHandshakeTimeoutException(cause: Throwable? = null) : TLSException("Timed out during handshake", cause)

/**
 * General error thrown when peer responds with unexpected data during TLS handshake.
 */
public class TLSValidationException(message: String, cause: Throwable? = null) : TLSException(message, cause)

/**
 * Thrown when encountering an unimplemented TLS feature.
 */
public class TLSUnsupportedException(message: String, cause: Throwable? = null) : TLSException(message, cause)

/**
 * Thrown when an alert is received from the connection.
 */
public class TLSAlertException(message: String, cause: Throwable? = null) : TLSException(message, cause)

/**
 * General error when negotiation fails to complete.
 */
public class TLSNegotiationException(message: String = "Negotiation failed", cause: Throwable? = null) : TLSException(
    message,
    cause
)
