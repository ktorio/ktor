/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kron.Reactor
import kotlin.coroutines.CoroutineContext

/**
 * Runs coroutines on [reactor]'s thread, each resumption posted to the reactor and run on its loop's next pass, from
 * that thread too: a coroutine resumed by another one runs after it, not inside it, the order Ktor's code assumes (a
 * WebSocket reader closes `incoming`, then sends its Close). kron resumes its own reads and flushes inline, without
 * the dispatcher, so a call that only reads and writes is never posted.
 */
internal class ReactorDispatcher(private val reactor: Reactor) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        reactor.post { block.run() }
    }

    override fun toString(): String = "ReactorDispatcher(${reactor.id})"
}
