/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

/**
 * Reports that current body in HTTP pipeline is invalid.
 */
public class InvalidBodyException(override val message: String) : Exception()

internal fun noBinaryDataException(
    expectedTypeName: String,
    subject: Any?
): InvalidBodyException = InvalidBodyException("Expected $expectedTypeName type but ${subject?.javaClass?.name} found")
