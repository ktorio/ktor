/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.client.*

internal suspend fun HttpClient.discoverVerified(issuer: String): OpenIdProviderMetadata =
    fetchOpenIdMetadata(issuer).also { metadata ->
        require(metadata.issuer == issuer) {
            "OpenID issuer mismatch: expected exactly $issuer, got ${metadata.issuer}"
        }
    }
