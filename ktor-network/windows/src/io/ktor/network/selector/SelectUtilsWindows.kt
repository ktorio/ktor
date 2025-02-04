/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import platform.posix.*
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal actual class SelectorHelper {
    private val wakeupSignal = SignalPoint()
    private val interestQueue = LockFreeMPSCQueue<EventInfo>()
    private val closeQueue = LockFreeMPSCQueue<Int>()
    private val allWsaEvents = ConcurrentMap<Int, COpaquePointer?>()

    actual fun interest(event: EventInfo): Boolean {
        if (interestQueue.addLast(event)) {
            wakeupSignal.signal()
            return true
        }

        return false
    }

    actual fun start(scope: CoroutineScope): Job {
        val job = scope.launch(CoroutineName("selector")) {
            selectionLoop()
        }

        job.invokeOnCompletion {
            cleanup()
        }

        return job
    }

    actual fun requestTermination() {
        interestQueue.close()
        wakeupSignal.signal()
    }

    private fun cleanup() {
        closeQueue.close()
        while (true) {
            val event = closeQueue.removeFirstOrNull() ?: break
            closeDescriptor(event)
        }
        wakeupSignal.close()
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

        while (!interestQueue.isClosed) {
            val wsaEvents = fillHandlersOrClose(watchSet, completed, closeSet)
            val index = memScoped {
                val length = wsaEvents.size + 1
                val wsaEventsWithWake = allocArray<CPointerVarOf<COpaquePointer>>(length).apply {
                    wsaEvents.values.forEachIndexed { index, wsaEvent ->
                        this[index] = wsaEvent
                    }
                    this[length - 1] = wakeupSignal.event
                }
                WSAWaitForMultipleEvents(
                    cEvents = length.convert(),
                    lphEvents = wsaEventsWithWake,
                    fWaitAll = 0,
                    dwTimeout = UInt.MAX_VALUE,
                    fAlertable = 0
                ).toInt().check()
            }

            processSelectedEvents(watchSet, completed, index, wsaEvents)
        }

        val exception = CancellationException("Selector closed")
        while (!interestQueue.isEmpty) {
            interestQueue.removeFirstOrNull()?.fail(exception)
        }

        for (item in watchSet) {
            item.fail(exception)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fillHandlersOrClose(
        watchSet: MutableSet<EventInfo>,
        completed: MutableSet<EventInfo>,
        closeSet: MutableSet<Int>,
    ): Map<Int, COpaquePointer?> {
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
                event.fail(IOException("Selectable closed"))
                completed.add(event)
            }
        }

        closeSet.clear()
        watchSet.removeAll(completed)
        completed.clear()

        return watchSet
            .groupBy { it.descriptor }
            .mapValues { (descriptor, events) ->
                val wsaEvent = allWsaEvents.computeIfAbsent(descriptor) {
                    WSACreateEvent()
                }
                if (wsaEvent == WSA_INVALID_EVENT) {
                    throw PosixException.forSocketError()
                }

                var lNetworkEvents = events.fold(0) { acc, event ->
                    acc or descriptorSetByInterestKind(event)
                }
                // Always add close event so selector gets notified on socket disconnect.
                lNetworkEvents = lNetworkEvents or FD_CLOSE

                WSAEventSelect(
                    s = descriptor.convert(),
                    hEventObject = wsaEvent,
                    lNetworkEvents = lNetworkEvents
                ).check { it == 0 }

                wsaEvent
            }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun processSelectedEvents(
        watchSet: MutableSet<EventInfo>,
        completed: MutableSet<EventInfo>,
        wsaIndex: Int,
        wsaEvents: Map<Int, COpaquePointer?>
    ) {
        watchSet.forEach { event ->
            val wsaEvent = wsaEvents.getValue(event.descriptor)
            val networkEvents = memScoped {
                val networkEvents = alloc<WSANETWORKEVENTS>()
                WSAEnumNetworkEvents(event.descriptor.convert(), wsaEvent, networkEvents.ptr).check()
                networkEvents.lNetworkEvents
            }

            val set = descriptorSetByInterestKind(event)

            val isClosed = networkEvents and FD_CLOSE != 0

            if (networkEvents and set == 0 && !isClosed) {
                return@forEach
            }

            completed.add(event)
            event.complete()
        }

        // The wake-up signal was added as the last event, so wsaIndex should be 1 higher than
        // the last index of wsaEvents.
        if (wsaIndex == wsaEvents.size) {
            wakeupSignal.check()
        }

        watchSet.removeAll(completed)
        completed.clear()
    }

    private fun descriptorSetByInterestKind(
        event: EventInfo
    ): Int = when (event.interest) {
        SelectInterest.READ -> FD_READ
        SelectInterest.WRITE -> FD_WRITE
        SelectInterest.ACCEPT -> FD_ACCEPT
        SelectInterest.CONNECT -> FD_CONNECT
    }

    private fun closeDescriptor(descriptor: Int) {
        close(descriptor)
        allWsaEvents.remove(descriptor)?.let { wsaEvent ->
            WSACloseEvent(wsaEvent)
        }
    }
}
