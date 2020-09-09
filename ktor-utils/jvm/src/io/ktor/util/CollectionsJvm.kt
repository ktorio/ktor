/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.util.*

/**
 * Wraps into an unmodifiable set
 */
@InternalAPI
public actual fun <T> Set<T>.unmodifiable(): Set<T> = Collections.unmodifiableSet(this)
