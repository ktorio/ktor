/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

/**
 * Represents a way to [write], [read] and [invalidate] session bits.
 */
public interface SessionStorage {
    /**
     * Writes a session [value] for [id].
     */
    public suspend fun write(id: String, value: String)

    /**
     * Invalidates session [id].
     *
     * This method prevents session [id] from being accessible after this call.
     *
     * @throws NoSuchElementException when session [id] is not found.
     */
    public suspend fun invalidate(id: String)

    /**
     * Reads session by [id]
     *
     * @throws NoSuchElementException when session [id] is not found.
     */
    public suspend fun read(id: String): String
}
