/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlin.time.*

internal sealed class Trigger(
    private val fileSystem: FileSystem,
    val pattern: FilePathPattern,
    protected val triggerCleanup: () -> Unit,
    protected val clock: () -> GMTDate
) : CachedFilesView.Listener {

    @Suppress("LeakingThis")
    protected val cache = CachedFilesView(fileSystem, pattern, this)

    protected val signal = Channel<Unit>(Channel.CONFLATED)

    final override fun changed(file: CachedFilesView.CachedFile) {
        if (check()) {
            triggerCleanup()
        }
        signal.offer(Unit)
    }

    class TotalCount(
        val maxCount: Int,
        fileSystem: FileSystem,
        pattern: FilePathPattern,
        listener: () -> Unit,
        clock: () -> GMTDate
    ) :
        Trigger(fileSystem, pattern, listener, clock) {

        override fun check(): Boolean {
            return cache.list().count() > maxCount
        }
    }

    class TotalSize(
        val maxSize: Long,
        fileSystem: FileSystem,
        pattern: FilePathPattern,
        listener: () -> Unit,
        clock: () -> GMTDate
    ) :
        Trigger(fileSystem, pattern, listener, clock) {

        override fun check(): Boolean {
            return computeSize() > maxSize
        }

        private fun computeSize(): Long = cache.list().fold(0L) { total, file ->
            val fileSize = file.knownSize
            val newSize = total + fileSize
            when {
                newSize < 0 || total == Long.MAX_VALUE -> Long.MAX_VALUE
                else -> newSize
            }
        }
    }

    class MaxAge @ExperimentalTime constructor(
        private val maxAge: Duration,
        fileSystem: FileSystem,
        pattern: FilePathPattern,
        listener: () -> Unit,
        clock: () -> GMTDate
    ) : Trigger(fileSystem, pattern, listener, clock) {

        @UseExperimental(ExperimentalTime::class)
        constructor(
            maxAgeMillis: Long,
            fileSystem: FileSystem,
            pattern: FilePathPattern,
            listener: () -> Unit,
            clock: () -> GMTDate
        ) : this(maxAgeMillis.milliseconds, fileSystem, pattern, listener, clock)

        @UseExperimental(ExperimentalTime::class)
        override fun setupImpl(scope: CoroutineScope) {
            scope.launch {
                while (isActive) {
                    if (check()) {
                        triggerCleanup()
                    } else {
                        val oldestAge = oldestFileAge() ?: 1.minutes
                        val delay = maxAge.toLongMilliseconds() - oldestAge.toLongMilliseconds()

                        select<Unit> {
                            signal.onReceive {
                            }
                            onTimeout(delay) {}
                        }
                    }
                }

            }
        }

        @UseExperimental(ExperimentalTime::class)
        override fun check(): Boolean {
            return oldestFileAge()?.let { it > maxAge } ?: false
        }

        @UseExperimental(ExperimentalTime::class)
        private fun oldestFileAge(): Duration? {
            val oldestFile = cache.list().minBy { it.lastModified } ?: return null
            val now = clock()
            return (now.timestamp - oldestFile.lastModified.timestamp).milliseconds
        }
    }

    protected open fun setupImpl(scope: CoroutineScope) {
    }

    internal fun setup(scope: CoroutineScope) {
        fileSystem.addListener(cache)
        Job(scope.coroutineContext[Job]).invokeOnCompletion {
            fileSystem.removeListener(cache)
            cache.invalidate()
        }

        setupImpl(scope)
    }

    internal abstract fun check(): Boolean
}
