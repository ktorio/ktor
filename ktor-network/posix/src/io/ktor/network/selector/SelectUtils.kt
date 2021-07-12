/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.network.selector

import io.ktor.network.interop.*
import io.ktor.network.util.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import platform.posix.*
import kotlin.coroutines.*
import kotlin.math.*
import kotlin.native.concurrent.*

@OptIn(InternalAPI::class)
internal class SelectorHelper {
    private val wakeupSignal = SignalPoint()
    private val interestQueue = LockFreeMPSCQueue<EventInfo>()

    private val wakeupSignalEvent = EventInfo(
        wakeupSignal.selectionDescriptor,
        SelectInterest.READ,
        Continuation(EmptyCoroutineContext) {
        }
    )

    init {
        makeShared()
    }

    fun interest(event: EventInfo): Boolean {
        if (interestQueue.addLast(event)) {
            wakeupSignal.signal()
            return true
        }

        return false
    }

    fun start(scope: CoroutineScope) {
        scope.launch(CoroutineName("selector")) {
            selectionLoop()
        }.invokeOnCompletion {
            cleanup()
        }
    }

    fun requestTermination() {
        interestQueue.close()
        wakeupSignal.signal()
    }

    private fun cleanup() {
        wakeupSignal.close()
    }

    private fun selectionLoop(): Unit = memScoped {
        val readSet = alloc<fd_set>()
        val writeSet = alloc<fd_set>()
        val errorSet = alloc<fd_set>()

        val completed = mutableListOf<EventInfo>()
        val watchSet = mutableSetOf<EventInfo>()

        while (!interestQueue.isClosed) {
            watchSet.add(wakeupSignalEvent)
            val maxDescriptor = fillHandlers(watchSet, readSet, writeSet, errorSet)
            if (maxDescriptor == 0) {
                continue
            }

            pselect(maxDescriptor + 1, readSet.ptr, writeSet.ptr, errorSet.ptr, null, null)
                .check()

            processSelectedEvents(watchSet, completed, readSet, writeSet, errorSet)
        }

        val exception = CancellationException("Selector closed").freeze()
        while (!interestQueue.isEmpty) {
            interestQueue.removeFirstOrNull()?.fail(exception)
        }

        for (item in watchSet) {
            item.fail(exception)
        }
    }

    private fun fillHandlers(
        watchSet: MutableSet<EventInfo>,
        readSet: fd_set,
        writeSet: fd_set,
        errorSet: fd_set
    ): Int {
        var maxDescriptor = 0

        select_fd_clear(readSet.ptr)
        select_fd_clear(writeSet.ptr)
        select_fd_clear(errorSet.ptr)

        while (true) {
            val event = interestQueue.removeFirstOrNull() ?: break
            watchSet.add(event)
        }

        for (event in watchSet) {
            addInterest(event, readSet, writeSet, errorSet)
            maxDescriptor = max(maxDescriptor, event.descriptor)
        }

        return maxDescriptor
    }

    private fun addInterest(
        event: EventInfo,
        readSet: fd_set,
        writeSet: fd_set,
        errorSet: fd_set
    ) {
        val set = descriptorSetByInterestKind(event, readSet, writeSet)

        select_fd_add(event.descriptor, set.ptr)
        select_fd_add(event.descriptor, errorSet.ptr)

        check(select_fd_isset(event.descriptor, set.ptr) != 0)
        check(select_fd_isset(event.descriptor, errorSet.ptr) != 0)
    }

    private fun processSelectedEvents(
        watchSet: MutableSet<EventInfo>,
        completed: MutableList<EventInfo>,
        readSet: fd_set,
        writeSet: fd_set,
        errorSet: fd_set
    ) {
        for (event in watchSet) {
            val set = descriptorSetByInterestKind(event, readSet, writeSet)

            if (select_fd_isset(event.descriptor, errorSet.ptr) != 0) {
                completed.add(event)
                @Suppress("DEPRECATION")
                event.fail(SocketError())
                continue
            }
            if (select_fd_isset(event.descriptor, set.ptr) != 0) {
                if (event.descriptor == wakeupSignal.selectionDescriptor) {
                    wakeupSignal.check()
                } else {
                    completed.add(event)
                    event.complete()
                }
                continue
            }
        }

        watchSet.removeAll(completed)
        completed.clear()
    }

    private fun descriptorSetByInterestKind(
        event: EventInfo,
        readSet: fd_set,
        writeSet: fd_set
    ) = when (event.interest) {
        SelectInterest.READ -> readSet
        SelectInterest.WRITE -> writeSet
        SelectInterest.ACCEPT -> readSet
        else -> error("Unsupported interest ${event.interest}.")
    }
}

@Deprecated("This will not be thrown since 2.0.0.")
public class SocketError : IllegalStateException()
