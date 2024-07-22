/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal actual class SelectorHelper {
    private val wakeupSignal = SignalPoint()
    private val interestQueue = LockFreeMPSCQueue<EventInfo>()
    private val closeQueue = LockFreeMPSCQueue<Int>()

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
        wakeupSignal.close()
    }

    actual fun notifyClosed(descriptor: Int) {
        closeQueue.addLast(descriptor)
        wakeupSignal.signal()
    }

    @OptIn(ExperimentalForeignApi::class, InternalAPI::class)
    private fun selectionLoop() {
        val completed = mutableSetOf<EventInfo>()
        val watchSet = mutableSetOf<EventInfo>()
        val closeSet = mutableSetOf<Int>()
        val allWsaEvents = mutableMapOf<Int, COpaquePointer?>()

        while (!interestQueue.isClosed) {
            val wsaEvents = fillHandlers(watchSet, allWsaEvents)
            val index = memScoped {
                val length = wsaEvents.size + 1
                val wsaEventsWithWake = allocArray<CPointerVarOf<COpaquePointer>>(length).apply {
                    wsaEvents.forEachIndexed { index, wsaEvent ->
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

            processSelectedEvents(watchSet, closeSet, completed, allWsaEvents, index, wsaEvents)
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
    private fun fillHandlers(
        watchSet: MutableSet<EventInfo>,
        allWsaEvents: MutableMap<Int, COpaquePointer?>
    ): List<COpaquePointer?> {
        while (true) {
            val event = interestQueue.removeFirstOrNull() ?: break
            watchSet.add(event)
        }
        return watchSet.map { event ->
            allWsaEvents.getOrPut(event.descriptor) {
                val wsaEvent = WSACreateEvent()
                WSAEventSelect(
                    s = event.descriptor.convert(),
                    hEventObject = wsaEvent,
                    lNetworkEvents = allInterestKinds
                )
                wsaEvent
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun processSelectedEvents(
        watchSet: MutableSet<EventInfo>,
        closeSet: MutableSet<Int>,
        completed: MutableSet<EventInfo>,
        allWsaEvents: MutableMap<Int, COpaquePointer?>,
        wsaIndex: Int,
        wsaEvents: List<COpaquePointer?>
    ) {
        while (true) {
            val event = closeQueue.removeFirstOrNull() ?: break
            closeSet.add(event)
        }

        watchSet.forEachIndexed { index, event ->
            if (event.descriptor in closeSet) {
                completed.add(event)
                return@forEachIndexed
            }
            val wsaEvent = wsaEvents[index]
            val networkEvents = memScoped {
                val networkEvents = alloc<WSANETWORKEVENTS>()
                WSAEnumNetworkEvents(event.descriptor.convert(), wsaEvent, networkEvents.ptr).check()
                networkEvents.lNetworkEvents
            }

            val set = descriptorSetByInterestKind(event)

            if (networkEvents and set == 0) {
                return@forEachIndexed
            }

            completed.add(event)
            event.complete()
        }

        if (wsaIndex == wsaEvents.lastIndex + 1) {
            wakeupSignal.check()
        }

        for (descriptor in closeSet) {
            close(descriptor)
            allWsaEvents.remove(descriptor)?.let { wsaEvent ->
                WSACloseEvent(wsaEvent)
            }
        }
        closeSet.clear()

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

    private companion object {
        private val allInterestKinds: Int = FD_READ or FD_WRITE or FD_ACCEPT or FD_CONNECT
    }
}
