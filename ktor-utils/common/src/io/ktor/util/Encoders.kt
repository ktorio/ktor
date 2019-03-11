package io.ktor.util

import kotlinx.coroutines.*
import kotlinx.coroutines.io.*

@KtorExperimentalAPI
/**
 * Empty [Encoder]
 */
object Identity : Encoder {
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel = source

    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel = source
}

/**
 * Content encoder.
 */
@KtorExperimentalAPI
interface Encoder {
    /**
     * Launch coroutine to encode [source] bytes.
     */
    fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel

    /**
     * Launch coroutine to decode [source] bytes.
     */
    fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel
}
