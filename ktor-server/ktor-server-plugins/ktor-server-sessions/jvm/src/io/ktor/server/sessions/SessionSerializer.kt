/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

/**
 * Serializes a session data from and to [String].
 *
 * @see [Sessions]
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
