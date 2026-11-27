/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine context element that overrides the dispatcher used by Ktor blocking bridges, such as
 * [OutgoingContent.WriteChannelContent] implementations backed by blocking streams, for the coroutines
 * it is installed in.
 *
 * The [dispatcher] is used as is, without any additional parallelism limit.
 */
@InternalAPI
public class BlockingDispatcherElement(public val dispatcher: CoroutineDispatcher) :
    AbstractCoroutineContextElement(Key) {

    public companion object Key : CoroutineContext.Key<BlockingDispatcherElement>
}
