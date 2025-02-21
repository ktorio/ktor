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

        try {
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
                    ).toInt().check(posixFunctionName = "WSAWaitForMultipleEvents")
                }

                processSelectedEvents(watchSet, completed, index, wsaEvents)
            }
        } finally {
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
                if (event.interest == SelectInterest.CLOSE) {
                    event.complete()
                } else {
                    event.fail(IOException("Selectable closed"))
                }
                completed.add(event)
            }
        }

        closeSet.clear()
        watchSet.removeAll(completed)
        completed.clear()

        return watchSet
            .filter { it.interest != SelectInterest.CLOSE }
            .groupBy { it.descriptor }
            .mapValues { (descriptor, events) ->
                val wsaEvent = allWsaEvents.computeIfAbsent(descriptor) {
                    WSACreateEvent()
                }
                if (wsaEvent == WSA_INVALID_EVENT) {
                    throw PosixException.forSocketError(posixFunctionName = "WSACreateEvent")
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
        watchSet
            .filter { it.interest != SelectInterest.CLOSE }
            .groupBy { it.descriptor }
            .forEach { (descriptor, events) ->
                val wsaEvent = wsaEvents.getValue(descriptor)

                val networkEvents = memScoped {
                    val networkEvents = alloc<WSANETWORKEVENTS>()
                    WSAEnumNetworkEvents(descriptor.convert(), wsaEvent, networkEvents.ptr)
                        .check(posixFunctionName = "WSAEnumNetworkEvents")
                    networkEvents.lNetworkEvents
                }

                for (event in events) {
                    val set = descriptorSetByInterestKind(event)

                    val isClosed = networkEvents and FD_CLOSE != 0

                    if (networkEvents and set == 0 && !isClosed) {
                        continue
                    }

                    completed.add(event)
                    event.complete()
                }
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
        SelectInterest.CLOSE -> error("Close should not be selected")
    }

    private fun closeDescriptor(descriptor: Int) {
        io.ktor.network.util.closeSocketDescriptor(descriptor)
        allWsaEvents.remove(descriptor)?.let { wsaEvent ->
            WSACloseEvent(wsaEvent)
        }
    }
}
