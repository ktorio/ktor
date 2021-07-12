// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

public actual class Lock {
    public actual fun lock() {}
    public actual fun unlock() {}
    public actual fun close() {}
}
