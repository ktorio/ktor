package io.ktor.utils.io.internal

import kotlinx.coroutines.*
import java.nio.*
import java.util.concurrent.atomic.*
import kotlin.reflect.*

internal fun ByteBuffer.isEmpty() = !hasRemaining()

internal inline fun <reified Owner : Any> longUpdater(p: KProperty1<Owner, Long>): AtomicLongFieldUpdater<Owner> {
    return AtomicLongFieldUpdater.newUpdater(Owner::class.java, p.name)
}

internal inline fun <reified Owner : Any> intUpdater(p: KProperty1<Owner, Int>): AtomicIntegerFieldUpdater<Owner> {
    return AtomicIntegerFieldUpdater.newUpdater(Owner::class.java, p.name)
}

internal inline fun <reified Owner : Any, reified T> updater(p: KProperty1<Owner, T>): AtomicReferenceFieldUpdater<Owner, T> {
    return AtomicReferenceFieldUpdater.newUpdater(Owner::class.java, T::class.java, p.name)
}

internal fun getIOIntProperty(name: String, default: Int): Int =
    try { System.getProperty("io.ktor.utils.io.$name") }
    catch (e: SecurityException) { null }
        ?.toIntOrNull() ?: default

@Suppress("LoopToCallChain")
internal fun ByteBuffer.indexOfPartial(sub: ByteBuffer): Int {
    val subPosition = sub.position()
    val subSize = sub.remaining()
    val first = sub[subPosition]
    val limit = limit()

    outer@for (idx in position() until limit) {
        if (get(idx) == first) {
            for (j in 1 until subSize) {
                if (idx + j == limit) break
                if (get(idx + j) != sub.get(subPosition + j)) continue@outer
            }
            return idx - position()
        }
    }

    return -1
}

@Suppress("LoopToCallChain")
internal fun ByteBuffer.startsWith(prefix: ByteBuffer, prefixSkip: Int = 0): Boolean {
    val size = minOf(remaining(), prefix.remaining() - prefixSkip)
    if (size <= 0) return false

    val position = position()
    val prefixPosition = prefix.position() + prefixSkip

    for (i in 0 until size) {
        if (get(position + i) != prefix.get(prefixPosition + i)) return false
    }

    return true
}

internal fun ByteBuffer.putAtMost(src: ByteBuffer, n: Int = src.remaining()): Int {
    val rem = remaining()
    val srcRem = src.remaining()

    return when {
        srcRem <= rem && srcRem <= n -> {
            put(src)
            srcRem
        }
        else -> {
            val size = minOf(rem, srcRem, n)
            for (idx in 1..size) {
                put(src.get())
            }
            size
        }
    }
}

internal fun ByteBuffer.putLimited(src: ByteBuffer, limit: Int = limit()): Int {
    return putAtMost(src, limit - src.position())
}

internal fun ByteArray.asByteBuffer(offset: Int = 0, length: Int = size): ByteBuffer = ByteBuffer.wrap(this, offset, length)
