/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.sessions.*
import io.ktor.utils.io.*

/**
 * Configures how a typed session authentication scheme transports session data.
 *
 * Assign one variant to [TypedSessionAuthConfig.transport]. Only one transport applies per scheme.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionTransportType)
 *
 * @param S the stored session type.
 */
@ExperimentalKtorApi
@SubclassOptInRequired
public open class SessionTransportType<out S : Any> {

    /**
     * Passes the serialized session in a cookie.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionTransportType.Cookie)
     *
     * @param block configures cookie settings, serialization, and transformations.
     */
    public class Cookie<S : Any>(
        public val block: CookieSessionBuilder<S>.() -> Unit = {},
    ) : SessionTransportType<S>()

    /**
     * Passes a session identifier in a cookie and stores session data on the server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionTransportType.CookieId)
     *
     * @param storage server-side session storage.
     * @param block configures cookie settings, serialization, transformations, and ID generation.
     */
    public class CookieId<S : Any>(
        public val storage: SessionStorage,
        public val block: CookieIdSessionBuilder<S>.() -> Unit = {},
    ) : SessionTransportType<S>()

    /**
     * Passes the serialized session in a header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionTransportType.Header)
     *
     * @param block configures header settings, serialization, and transformations.
     */
    public class Header<S : Any>(
        public val block: HeaderSessionBuilder<S>.() -> Unit = {},
    ) : SessionTransportType<S>()

    /**
     * Passes a session identifier in a header and stores session data on the server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionTransportType.HeaderId)
     *
     * @param storage server-side session storage.
     * @param block configures header settings, serialization, transformations, and ID generation.
     */
    public class HeaderId<S : Any>(
        public val storage: SessionStorage,
        public val block: HeaderIdSessionBuilder<S>.() -> Unit = {},
    ) : SessionTransportType<S>()
}
