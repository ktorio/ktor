/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Creates the selector manager for current platform.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.SelectorManager)
 */
public expect fun SelectorManager(
    dispatcher: CoroutineContext = EmptyCoroutineContext
): SelectorManager

/**
 * SelectorManager interface allows [Selectable] wait for [SelectInterest].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.SelectorManager)
 */
public expect interface SelectorManager : CoroutineScope, Closeable {
    /**
     * Notifies the selector that selectable has been closed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.SelectorManager.notifyClosed)
     */
    public fun notifyClosed(selectable: Selectable)

    /**
     * Suspends until [interest] is selected for [selectable]
     * May cause manager to allocate and run selector instance if not yet created.
     *
     * Only one selection is allowed per [interest] per [selectable] but you can
     * select for different interests for the same selectable simultaneously.
     * In other words you can select for read and write at the same time but should never
     * try to read twice for the same selectable.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.SelectorManager.select)
     */
    public suspend fun select(selectable: Selectable, interest: SelectInterest)

    public companion object
}

/**
 * Select interest kind.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.SelectInterest)
 */
public expect enum class SelectInterest {
    READ,
    WRITE,
    ACCEPT,
    CONNECT;

    public companion object {
        public val AllInterests: Array<SelectInterest>
    }
}
