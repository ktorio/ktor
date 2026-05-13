/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef

@OptIn(ExperimentalForeignApi::class)
internal inline fun <T : Any> T.asStablePointer(): COpaquePointer = StableRef.create(this).asCPointer()

@OptIn(ExperimentalForeignApi::class)
internal inline fun <reified T : Any> COpaquePointer.fromCPointer(): T = asStableRef<T>().get()
