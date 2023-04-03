/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.util

import io.ktor.util.logging.*

internal fun <T : Any> T.logger() = KtorSimpleLogger(this::class.simpleName ?: "KtorSimpleLogger")
