package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.cio.*

interface SessionStorage {
    suspend fun write(id: String, provider: suspend (WriteChannel) -> Unit)
    suspend fun <R> read(id: String, consumer: suspend (ReadChannel) -> R): R
    suspend fun invalidate(id: String)
}

