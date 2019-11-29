/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.jvm.*
import kotlin.time.*

@UseExperimental(ExperimentalTime::class)
internal abstract class AbstractRollingFileAppender constructor(
    private val fileSystem: FileSystem,
    private val backgroundTasksScope: CoroutineContext,
    private val logFileName: String,
    internal val rolledFilePathPattern: FilePathPattern,
    private val recordEncoder: RecordEncoder = RecordEncoder.Default,
    internal val policy: RollingPolicy = RollingPolicy(),
    private val clock: () -> GMTDate = { GMTDate() }
) : Appender, CoroutineScope {
    @Volatile
    private var current: LogFile? = null
    private var deferredFiles = HashMap<String, LogFile>()
    private val mask = FilePathPattern(rolledFilePathPattern.parts
        .filter { it is FilePathPattern.Component.Date || it is FilePathPattern.Component.Number })
    private var triggers: List<Trigger> = emptyList()
    private val cached = CachedFilesView(fileSystem, rolledFilePathPattern, object : CachedFilesView.Listener {
        override fun changed(file: CachedFilesView.CachedFile) {
            triggers.forEach {
                it.changed(file)
            }
        }
    })
    private val kClock = Clock()

    final override val coroutineContext: CoroutineContext
        get() = backgroundTasksScope

    private val checkSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        launch {
            while (isActive) {
                val delayMark = kClock.markNow() + 10.seconds
                println("waiting for signal...")
                checkSignal.receive()

                if (delayMark.hasNotPassedNow()) {
                    val delayInMillis = delayMark.elapsedNow().toLongMilliseconds()
                    if (delayInMillis < 0L) {
                        println("delaying ${-delayInMillis} ms")
                        delay(-delayInMillis) // it is negative since end mark is in the future
                        checkSignal.poll() // clear signal flag
                    }
                }

                println("checking file...")
                checkFile()
            }
        }

        fileSystem.addListener(cached)
        coroutineContext[Job]?.let {
            it.invokeOnCompletion {
                fileSystem.removeListener(cached)
            }
        }

        triggers += Trigger.TotalCount(
            policy.maxTotalCount, fileSystem, rolledFilePathPattern, { checkSignal.offer(Unit) }, clock
        )
        triggers += Trigger.TotalSize(
            policy.maxTotalSize.bytesCount, fileSystem, rolledFilePathPattern, { checkSignal.offer(Unit) }, clock
        )
        triggers += Trigger.MaxAge(
            policy.keepUntil, fileSystem, rolledFilePathPattern, { checkSignal.offer(Unit) }, clock
        )

        triggers.forEach {
            it.setup(this)
        }
    }

    final override fun append(record: LogRecord) {
        writeRecord(record, dispatch(record).output)
    }

    final override fun flush() {
        current?.apply {
            flush()
            println("sending signal")
            checkSignal.offer(Unit)
        }
        deferredFiles.values.forEach {
            it.flush()
        }
    }

    private fun checkFile() {
        val fileSize = fileSystem.size(logFileName)
        if (fileSize >= policy.maxFileSize.bytesCount && current != null) {
            rollCurrent()
        }

        deferredFiles.values.takeUnless { it.isEmpty() }?.toList()?.forEach { file ->
            file.deferredMark?.let { mark ->
                if (mark.elapsedNow() > 1.minutes) {
                    file.close()
                    deferredFiles.remove(file.mask)
                }
            }
        }

        if (triggers.any { it.check() }) {
            removeOld()
        }
    }

    private fun writeRecord(record: LogRecord, output: Output) {
        recordEncoder.encode(record, output)
    }

    private fun dispatch(record: LogRecord): LogFile {
        val recordDate = record.logTime ?: clock()
        val mask = mask(recordDate)

        current?.let {
            if (it.mask == mask) return it
        }

        deferredFiles[mask]?.let { found ->
            check(found.mask == mask)
            if (current != null) {
                return found
            }
        }

        current?.let {
            if (it.date < recordDate) {
                return dispatchWithRoll(mask, recordDate)
            }
            return dispatchToOld(recordDate, mask)
        }

        return openNew(mask, recordDate)
    }

    private fun dispatchWithRoll(newRecordMask: String, recordDate: GMTDate): LogFile {
        rollCurrent()
        return openNew(newRecordMask, recordDate)
    }

    private fun rollCurrent() {
        val current = current!!
        current.close()
        rollFiles(fileSystem, logFileName, cached, rolledFilePathPattern, current.date)
        deferredFiles[current.mask] = reopen(current).also { it.defer() }
        this.current = null
    }

    private fun removeOld() {
        cached.list().sortedBy { it.lastModified }.forEach { file ->
            fileSystem.delete(file.path)
            if (triggers.none { it.check() }) {
                return
            }
        }
    }

    private fun dispatchToOld(recordDate: GMTDate, newRecordMask: String): LogFile {
        val fileName = rolledFilePathPattern.format(recordDate, 1)
        val old = openFile(newRecordMask, fileName, 1, recordDate)
        old.defer()
        deferredFiles[newRecordMask] = old
        return old
    }

    private fun reopen(current: LogFile): LogFile =
        openFile(current.mask, rolledFilePathPattern.format(current.date, 1), 1, current.date)

    private fun openNew(mask: String, date: GMTDate): LogFile {
        current?.close()

        val file = openFile(mask, logFileName, 0, date)
        current = file

        return file
    }

    private fun openFile(mask: String, fileName: String, number: Int, date: GMTDate): LogFile =
        fileSystem.open(fileName).let { output ->
            LogFile(output, fileName, mask, number, date)
        }

    private fun mask(date: GMTDate): String = mask.format(date, 0)

    private inner class LogFile(
        val output: Output,
        val fileName: String,
        val mask: String,
        val number: Int,
        val date: GMTDate
    ) {
        var deferredMark: ClockMark? = null
            private set

        fun defer() {
            check(deferredMark == null)
            deferredMark = kClock.markNow()
        }

        fun close() {
            output.close()
        }

        fun flush() {
            output.flush()
        }
    }

    private inner class Clock : AbstractLongClock(DurationUnit.MILLISECONDS) {
        override fun read(): Long = clock().timestamp
    }
}
