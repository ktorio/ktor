/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.interop.*
import io.ktor.network.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import platform.posix.*
import kotlin.coroutines.*
import kotlin.math.*

@OptIn(InternalAPI::class)
internal actual class SelectorHelper {
    private val fdSetSize: Int

    @OptIn(ExperimentalForeignApi::class)
    actual constructor() : this(fdSetSize = fd_setsize())

    constructor(fdSetSize: Int) {
        this.fdSetSize = fdSetSize
    }

    private val wakeupSignal = SignalPoint()
    private val interestQueue = LockFreeMPSCQueue<EventInfo>()
    private val closeQueue = LockFreeMPSCQueue<Int>()

    private val wakeupSignalEvent = EventInfo(
        wakeupSignal.selectionDescriptor,
        SelectInterest.READ,
        Continuation(EmptyCoroutineContext) {
        }
    )

    actual fun interest(event: EventInfo): Boolean {
        if (interestQueue.addLast(event)) {
            wakeupSignal.signal()
            return true
        }

        return false
    }

    actual fun start(scope: CoroutineScope): Job {
        return scope.launch(CoroutineName("selector")) {
            selectionLoop()
        }
    }

    actual fun requestTermination() {
        interestQueue.close()
        wakeupSignal.signal()
    }

    actual fun notifyClosed(descriptor: Int) {
        if (closeQueue.addLast(descriptor)) {
            wakeupSignal.signal()
        } else {
            closeDescriptor(descriptor)
        }
    }

    @OptIn(ExperimentalForeignApi::class, InternalAPI::class)
    private fun selectionLoop() {
        val completed = mutableSetOf<EventInfo>()
        val watchSet = mutableSetOf<EventInfo>()
        val closeSet = mutableSetOf<Int>()

        val readSet = select_create_fd_set()
        val writeSet = select_create_fd_set()
        val errorSet = select_create_fd_set()

        try {
            while (!interestQueue.isClosed) {
                watchSet.add(wakeupSignalEvent)
                val maxDescriptor = fillHandlersOrClose(watchSet, completed, closeSet, readSet, writeSet, errorSet)

                try {
                    selector_pselect(maxDescriptor + 1, readSet, writeSet, errorSet)
                        .check(posixFunctionName = "pselect")
                } catch (_: PosixException.BadFileDescriptorException) {
                    // Thrown if any of the descriptors was closed.
                    // This means the sets are undefined so do not rely on their contents.
                    watchSet.forEach { event ->
                        if (!isDescriptorValid(event.descriptor)) {
                            completed.add(event)
                            event.fail(IOException("Bad descriptor ${event.descriptor} for ${event.interest}"))
                        }
                    }
                    watchSet.removeAll(completed)
                    completed.clear()
                    continue
                }

                processSelectedEvents(watchSet, completed, readSet, writeSet, errorSet)
            }
        } finally {
            selector_release_fd_set(readSet)
            selector_release_fd_set(writeSet)
            selector_release_fd_set(errorSet)

            closeQueue.close()
            wakeupSignal.close()
            interestQueue.close()
            while (true) {
                val event = closeQueue.removeFirstOrNull() ?: break
                closeSet.add(event)
            }
            while (true) {
                val event = interestQueue.removeFirstOrNull() ?: break
                watchSet.add(event)
            }
            for (descriptor in closeSet) {
                closeDescriptor(descriptor)
            }
        }

        val exception = IOException("Selector closed")
        for (event in watchSet) {
            if (event.descriptor in closeSet) {
                if (event.interest == SelectInterest.CLOSE) {
                    event.complete()
                } else {
                    event.fail(IOException("Selectable closed"))
                }
            } else {
                event.fail(exception)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fillHandlersOrClose(
        watchSet: MutableSet<EventInfo>,
        completed: MutableSet<EventInfo>,
        closeSet: MutableSet<Int>,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>,
        errorSet: CValue<selection_set>
    ): Int {
        var maxDescriptor = 0

        select_fd_clear(readSet)
        select_fd_clear(writeSet)
        select_fd_clear(errorSet)

        while (true) {
            val event = closeQueue.removeFirstOrNull() ?: break
            closeSet.add(event)
        }
        while (true) {
            val event = interestQueue.removeFirstOrNull() ?: break
            watchSet.add(event)
        }

        for (descriptor in closeSet) {
            closeDescriptor(descriptor)
        }

        for (event in watchSet) {
            if (event.descriptor in closeSet) {
                if (event.interest == SelectInterest.CLOSE) {
                    event.complete()
                } else {
                    event.fail(IOException("Selectable closed"))
                }
                completed.add(event)
            } else if (event.interest != SelectInterest.CLOSE) {
                addInterest(event, readSet, writeSet, errorSet)
                maxDescriptor = max(maxDescriptor, event.descriptor)
            }
        }

        closeSet.clear()
        watchSet.removeAll(completed)
        completed.clear()

        return maxDescriptor
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun addInterest(
        event: EventInfo,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>,
        errorSet: CValue<selection_set>
    ) {
        val set = descriptorSetByInterestKind(event, readSet, writeSet)

        check(event.descriptor >= 0) {
            "File descriptor ${event.descriptor} is negative"
        }
        check(event.descriptor < fdSetSize) {
            "File descriptor ${event.descriptor} is larger or equal to FD_SETSIZE ($fdSetSize)"
        }

        select_fd_add(event.descriptor, set)
        select_fd_add(event.descriptor, errorSet)

        check(select_fd_isset(event.descriptor, set) != 0)
        check(select_fd_isset(event.descriptor, errorSet) != 0)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun processSelectedEvents(
        watchSet: MutableSet<EventInfo>,
        completed: MutableSet<EventInfo>,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>,
        errorSet: CValue<selection_set>
    ) {
        for (event in watchSet) {
            if (event.interest == SelectInterest.CLOSE) continue

            val set = descriptorSetByInterestKind(event, readSet, writeSet)

            if (select_fd_isset(event.descriptor, errorSet) != 0) {
                completed.add(event)
                event.fail(IOException("Fail to select descriptor ${event.descriptor} for ${event.interest}"))
                continue
            }

            if (select_fd_isset(event.descriptor, set) == 0) continue

            if (event.descriptor == wakeupSignal.selectionDescriptor) {
                wakeupSignal.check()
                continue
            }

            completed.add(event)
            event.complete()
        }

        watchSet.removeAll(completed)
        completed.clear()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun descriptorSetByInterestKind(
        event: EventInfo,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>
    ): CValue<selection_set> = when (event.interest) {
        SelectInterest.READ -> readSet
        SelectInterest.WRITE -> writeSet
        SelectInterest.ACCEPT -> readSet
        SelectInterest.CONNECT -> writeSet
        SelectInterest.CLOSE -> error("Close should not be selected")
    }

    private fun closeDescriptor(descriptor: Int) {
        close(descriptor)
    }

    private fun isDescriptorValid(descriptor: Int): Boolean {
        return fcntl(descriptor, F_GETFL) != -1 || errno != EBADF
    }
}
