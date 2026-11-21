/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.utils.io.errors.UnknownServiceException

/**
 * Returns whether cleartext network traffic (e.g. HTTP, FTP, XMPP, IMAP, SMTP -- without TLS or STARTTLS) is permitted
 * for communicating with hostname for this process.
 *
 * See [NetworkSecurityPolicy#isCleartextTrafficPermitted](https://developer.android.com/reference/android/security/NetworkSecurityPolicy#isCleartextTrafficPermitted(java.lang.String)).
 */
internal expect fun isCleartextTrafficPermitted(hostname: String): Boolean

/**
 * Used by Android to determine whether cleartext network traffic is permitted for all network communication from this
 * process.
 */
public val CleartextTraffic: ClientPlugin<Unit> = createClientPlugin("CleartextTraffic") {
    onRequest { request, _ ->
        val host = request.url.host
        if (!isCleartextTrafficPermitted(host)) {
            throw UnknownServiceException("CLEARTEXT communication to $host not permitted by network security policy")
        }
    }
}
