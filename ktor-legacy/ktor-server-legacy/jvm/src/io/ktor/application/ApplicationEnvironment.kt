/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.application

import io.ktor.events.*
import io.ktor.config.*
import org.slf4j.*
import kotlin.coroutines.*

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationEnvironment", "io.ktor.server.application.*")
)
public interface ApplicationEnvironment
