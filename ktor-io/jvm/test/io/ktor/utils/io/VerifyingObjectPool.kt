/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.pool.*
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.model.*
import java.util.concurrent.*

internal actual fun identityHashCode(instance: Any) = System.identityHashCode(instance)

actual class VerifyingObjectPool<T : Any> actual constructor(
    delegate: ObjectPool<T>
) : VerifyingPoolBase<T>(delegate), TestRule {
    override val allocated = ConcurrentHashMap<IdentityWrapper<T>, Boolean>().keySet(true)!!

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                var failed = false
                try {
                    base.evaluate()
                } catch (t: Throwable) {
                    failed = true
                    try {
                        assertEmpty()
                    } catch (emptyFailed: Throwable) {
                        throw MultipleFailureException(listOf(t, emptyFailed))
                    }
                    throw t
                } finally {
                    if (!failed) {
                        assertEmpty()
                    }
                }
            }
        }
    }
}
