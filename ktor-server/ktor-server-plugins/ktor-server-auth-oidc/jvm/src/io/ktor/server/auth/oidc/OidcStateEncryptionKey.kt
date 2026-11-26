/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.server.auth.*

/**
 * Symmetric key material used to encrypt the temporary OAuth state cookie.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcStateEncryptionKey)
 */
@Deprecated(
    "The key is shared by authentication plugins and moved to ktor-server-auth.",
    ReplaceWith("AuthenticationStateEncryptionKey", "io.ktor.server.auth.AuthenticationStateEncryptionKey")
)
public typealias OidcStateEncryptionKey = AuthenticationStateEncryptionKey
