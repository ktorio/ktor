/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

internal class TestLogger : Logger {
    private val state = StringBuilder()

    override fun log(message: String) {
        state.append("$message\n")
    }

    fun dump(): String = state.toString()
}
