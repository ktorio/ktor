/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.util.*

private typealias Factory = HttpClientEngineFactory<HttpClientEngineConfig>

@InternalAPI
/**
 * Shared engines collection.
 * Use [append] to enable engine auto discover in [HttpClient()].
 */
public val engines: MutableList<Factory> = mutableListOf()
