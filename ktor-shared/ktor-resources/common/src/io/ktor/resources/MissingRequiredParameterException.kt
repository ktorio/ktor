/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

import kotlinx.serialization.SerializationException

/**
 * Thrown when a required resource parameter is absent from the request.
 *
 * Extends [SerializationException], unlike [ResourceSerializationException], since it takes the place of
 * kotlinx.serialization's own `MissingFieldException` for this case and must stay catchable as such.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.resources.MissingRequiredParameterException)
 */
public class MissingRequiredParameterException(message: String) : SerializationException(message)
