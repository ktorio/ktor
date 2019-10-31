/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.util.logging.*
import io.ktor.util.logging.Logger

internal class TestLogger : Logger(Config(StringBuilderAppender())) {
    fun dump(): String = (config.appender as StringBuilderAppender).build()
}
