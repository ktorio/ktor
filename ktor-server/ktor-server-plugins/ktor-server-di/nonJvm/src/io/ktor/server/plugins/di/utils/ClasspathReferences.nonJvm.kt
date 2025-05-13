/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*

internal actual fun Application.installReference(
    registry: DependencyRegistry,
    reference: ClasspathReference
): Unit = error("Reflection is not supported on this platform.")
