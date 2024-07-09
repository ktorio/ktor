/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.internal

import kotlinx.atomicfu.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal object UNDECIDED
internal object RESUMED

@PublishedApi
@SinceKotlin("1.3")
internal class KtorSafeContinuation : Continuation<Unit> {
    private lateinit var delegate: Continuation<Unit>

    override val context: CoroutineContext
        get() = delegate.context

    private val contResult: AtomicRef<Any?> = atomic(UNDECIDED)

    fun init(delegate: Continuation<Unit>) {
        this.delegate = delegate
        contResult.value = UNDECIDED
    }

    override fun resumeWith(result: Result<Unit>) {
        while (true) { // lock-free loop
            val cur = contResult.value // atomic read
            when {
                cur === UNDECIDED -> if (contResult.compareAndSet(UNDECIDED, result.getOrThrow())) return
                cur === COROUTINE_SUSPENDED -> if (contResult.compareAndSet(COROUTINE_SUSPENDED, RESUMED)) {
                    delegate.resumeWith(result)
                    return
                }
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    @PublishedApi
    internal fun getOrThrow(): Any? {
        var value = contResult.value // atomic read
        if (value === UNDECIDED) {
            if (contResult.compareAndSet(UNDECIDED, COROUTINE_SUSPENDED)) return COROUTINE_SUSPENDED
            value = contResult.value // reread volatile var
        }
        return when {
            value === RESUMED -> COROUTINE_SUSPENDED // already called continuation, indicate COROUTINE_SUSPENDED upstream
            (value is Result<*>) && value.isFailure -> value.getOrThrow()
            else -> value // either COROUTINE_SUSPENDED or data
        }
    }

    override fun toString(): String =
        "KtorSafContinuation for $delegate"
}
