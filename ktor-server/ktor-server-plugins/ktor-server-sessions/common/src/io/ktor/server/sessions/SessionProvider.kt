/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import kotlin.reflect.*

/**
 * Specifies a provider for a session with the specific [name] and [type].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionProvider)
 *
 * @param transport specifies the [SessionTransport] for this provider
 * @param tracker specifies the [SessionTracker] for this provider
 * @param sendOnlyIfModified when set to `true`, session data is not re-sent if unchanged from the incoming value.
 * This avoids unnecessary `Set-Cookie` headers but prevents cookie expiration refresh.
 * Session classes should properly implement `equals()` for this to work correctly.
 * Default: `false`.
 * @property name session name
 * @property type session instance type
 */
public class SessionProvider<S : Any>(
    public val name: String,
    public val type: KClass<S>,
    public val transport: SessionTransport,
    public val tracker: SessionTracker<S>,
    public val sendOnlyIfModified: Boolean = false
) {
    override fun toString(): String {
        return "SessionProvider(name = $name, type = $type, transport = $transport, tracker = $tracker)"
    }
}
