/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

internal expect class AtomicBoolean(value: Boolean) {

    val value: Boolean

    fun compareAndSet(expect: Boolean, update: Boolean): Boolean
}
