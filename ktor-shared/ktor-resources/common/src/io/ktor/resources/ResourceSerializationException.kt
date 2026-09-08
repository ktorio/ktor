/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

/**
 * Thrown when [de]serialization of the resource failed
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.resources.ResourceSerializationException)
 */
public class ResourceSerializationException : Exception {
    /**
     * @param message describes the reason (de)serialization failed
     */
    public constructor(message: String) : super(message)

    /**
     * @param message describes the reason (de)serialization failed
     * @param cause the original exception that caused (de)serialization to fail, preserved as [Throwable.cause]
     */
    public constructor(message: String, cause: Throwable) : super(message, cause)
}
