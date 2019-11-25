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
import kotlin.time.*

@UseExperimental(ExperimentalTime::class)
internal abstract class AbstractRollingFileAppender constructor(
    private val fileSystem: FileSystem,
    private val backgroundTasksScope: CoroutineContext,
    private val logFileName: String,
    private val rolledFilePathPattern: FilePathPattern,
    private val recordEncoder: RecordEncoder = RecordEncoder.Default,
    private val policy: RollingPolicy = RollingPolicy(),
    private val clock: Clock = MonoClock
) : Appender, CoroutineScope {
    private var current: LogFile? = null
    private var deferredFiles = HashMap<String, LogFile>()
    private val mask = FilePathPattern(rolledFilePathPattern.parts
        .filter { it is FilePathPattern.Component.Date || it is FilePathPattern.Component.Number })
    private val cached = CachedFilesView(fileSystem, rolledFilePathPattern, null) // TODO pass triggers

    final override val coroutineContext: CoroutineContext
        get() = backgroundTasksScope

    private val checkSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        launch {
            while (isActive) {
                val delayMark = clock.markNow() + 10.seconds
                checkSignal.receive()

                if (delayMark.hasNotPassedNow()) {
                    val delayInMillis = delayMark.elapsedNow().toLongMilliseconds()
                    if (delayInMillis < 0L) {
                        delay(-delayInMillis) // it is negative since end mark is in the future
                        checkSignal.poll() // clear signal flag
                    }
                }

                checkFile()
            }
        }
    }

    final override fun append(record: LogRecord) {
        writeRecord(record, dispatch(record).output)
    }

    final override fun flush() {
        current?.apply {
            flush()
            checkSignal.offer(Unit)
        }
        deferredFiles.values.forEach {
            it.flush()
        }
    }

    private fun checkFile() {
        val fileSize = fileSystem.size(logFileName)
        if (fileSize >= policy.maxFileSize.bytesCount) {
            rollCurrent()
            return
        }

        deferredFiles.values.takeUnless { it.isEmpty() }?.toList()?.forEach { file ->
            file.deferredMark?.let { mark ->
                if (mark.elapsedNow() > 1.minutes) {
                    file.close()
                    deferredFiles.remove(file.mask)
                }
            }
        }

        // TODO triggers
    }

    private fun writeRecord(record: LogRecord, output: Output) {
        recordEncoder.encode(record, output)
    }

    private fun dispatch(record: LogRecord): LogFile {
        val recordDate = record.logTime ?: GMTDate()
        val mask = mask(recordDate)

        current?.let {
            if (it.mask == mask) return it
        }

        deferredFiles[mask]?.let { found ->
            check(found.mask == mask)
            return found
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
            deferredMark = clock.markNow()
        }

        fun close() {
            output.close()
        }

        fun flush() {
            output.flush()
        }
    }
}
