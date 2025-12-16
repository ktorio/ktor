/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.openapi.HttpSecurityScheme
import io.ktor.openapi.SecurityScheme
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.DigestAuthenticationProvider
import io.ktor.server.auth.jwt.JWTAuthenticationProvider

internal actual fun Application.inferPlatformSpecificSecurityScheme(provider: AuthenticationProvider): SecurityScheme? {
    return when {
        provider is DigestAuthenticationProvider -> HttpSecurityScheme(
            scheme = "digest",
            description = provider.description ?: HttpSecurityScheme.DEFAULT_DIGEST_DESCRIPTION
        )
        // Catch an exception thrown when the auth-jwt plugin is not installed
        else -> runCatching { inferJwtScheme(provider) }.getOrElse { null }
    }
}

private fun inferJwtScheme(provider: AuthenticationProvider): SecurityScheme? {
    if (provider !is JWTAuthenticationProvider) {
        return null
    }
    return HttpSecurityScheme(
        scheme = "bearer",
        bearerFormat = "JWT",
        description = provider.description ?: HttpSecurityScheme.DEFAULT_JWT_DESCRIPTION
    )
}

/**
 * Registers a Digest HTTP authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param description Optional description for the security scheme. Defaults to [HttpSecurityScheme.DEFAULT_DIGEST_DESCRIPTION].
 */
public fun Application.registerDigestAuthSecurityScheme(
    name: String? = null,
    description: String = HttpSecurityScheme.DEFAULT_DIGEST_DESCRIPTION
) {
    registerSecurityScheme(name, HttpSecurityScheme(scheme = "digest", description = description))
}

/**
 * Registers a JWT Bearer authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param description Optional description for the security scheme. Defaults to [HttpSecurityScheme.DEFAULT_JWT_DESCRIPTION].
 */
public fun Application.registerJWTSecurityScheme(
    name: String? = null,
    description: String = HttpSecurityScheme.DEFAULT_JWT_DESCRIPTION
) {
    registerSecurityScheme(
        name,
        HttpSecurityScheme(scheme = "bearer", bearerFormat = "JWT", description = description)
    )
}
