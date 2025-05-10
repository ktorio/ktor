/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.internal

/**
 * Used to be thrown when a second attempt to read the body was made while the first call was blocked.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.internal.SaveBodyAbandonedReadException)
 */
@Deprecated("This exception is deprecated and is never thrown")
public class SaveBodyAbandonedReadException : RuntimeException("Save body abandoned")
