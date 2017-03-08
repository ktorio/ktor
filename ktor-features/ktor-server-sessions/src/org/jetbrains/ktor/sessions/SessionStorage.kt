package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.cio.*
import java.util.concurrent.*

interface SessionStorage {
    suspend fun save(id: String, provider: suspend (WriteChannel) -> Unit)
    suspend fun <R> read(id: String, consumer: suspend (ReadChannel) -> R): R
    suspend fun invalidate(id: String)
}

