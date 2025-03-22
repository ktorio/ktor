/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import java.io.Closeable

class CloseableGroup : Closeable {
    private val resources = mutableListOf<Closeable>()

    fun use(resource: Closeable) {
        resources.add(resource)
    }

    override fun close() {
        resources.forEach { it.close() }
    }
}
