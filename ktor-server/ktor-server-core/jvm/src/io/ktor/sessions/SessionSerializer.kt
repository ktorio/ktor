/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

/**
 * Serializes session from and to [String]
 */
interface SessionSerializer<T> {
    /**
     * Serializes a complex arbitrary object into a [String].
     */
    fun serialize(session: T): String

    /**
     * Deserializes a complex arbitrary object from a [String].
     */
    fun deserialize(text: String): T
}
