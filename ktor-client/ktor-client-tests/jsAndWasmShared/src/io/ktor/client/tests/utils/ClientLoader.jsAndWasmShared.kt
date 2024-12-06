// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import io.ktor.utils.io.*

@OptIn(InternalAPI::class)
internal actual val enginesToTest: Iterable<HttpClientEngineFactory<HttpClientEngineConfig>> get() = engines
internal actual val platformName: String get() = "web"
