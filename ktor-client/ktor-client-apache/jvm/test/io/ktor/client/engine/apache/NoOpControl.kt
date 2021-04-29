/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.apache

import org.apache.http.nio.*

internal class NoOpControl : IOControl {
    override fun requestInput() {
    }

    override fun suspendInput() {
    }

    override fun requestOutput() {
    }

    override fun suspendOutput() {
    }

    override fun shutdown() {
    }
}
