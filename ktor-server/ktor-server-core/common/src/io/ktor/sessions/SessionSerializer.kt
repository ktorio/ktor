/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

/**
 * Serializes session from and to [String]
 */
public interface SessionSerializer<T> {
    /**
     * Serializes a complex arbitrary object into a [String].
     */
    public fun serialize(session: T): String

    /**
     * Deserializes a complex arbitrary object from a [String].
     */
    public fun deserialize(text: String): T
}
