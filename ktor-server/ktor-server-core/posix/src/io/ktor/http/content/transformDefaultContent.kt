/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.application.*
import io.ktor.util.pipeline.*

/**
 * Default outgoing content transformation
 */
internal actual fun PipelineContext<Any, ApplicationCall>.transformDefaultContentPlatform(
    value: Any
): OutgoingContent? = null
