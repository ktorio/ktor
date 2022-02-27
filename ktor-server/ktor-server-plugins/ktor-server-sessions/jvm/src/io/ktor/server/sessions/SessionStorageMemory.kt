/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import java.util.concurrent.*

/**
 * [SessionStorage] that stores session contents into memory.
 *
 * Since it doesn't use any TTL sessions, memory usage will increase while the application is running
 * and session information will be discarded once the server stops.
 *
 * This is intended for development.
 */
public class SessionStorageMemory : SessionStorage {
    private val sessions = ConcurrentHashMap<String, String>()

    override suspend fun write(id: String, value: String) {
        sessions[id] = value
    }

    override suspend fun read(id: String): String =
        sessions[id] ?: throw NoSuchElementException("Session $id not found")

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
