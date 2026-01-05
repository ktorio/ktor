/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.openapi.SecurityScheme
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationProvider

internal actual fun Application.inferPlatformSpecificSecurityScheme(provider: AuthenticationProvider): SecurityScheme? {
    // No platform-specific metadata for authentication providers on non-JVM platforms.
    return null
}
