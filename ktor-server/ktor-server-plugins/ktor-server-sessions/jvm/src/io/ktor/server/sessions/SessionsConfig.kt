/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.util.*

/**
 * Sessions configuration builder
 */
@KtorDsl
public class SessionsConfig {
    private val registered = ArrayList<SessionProvider<*>>()

    /**
     * List of session providers to be registered
     */
    public val providers: List<SessionProvider<*>> get() = registered.toList()

    /**
     * Register a session [provider]
     */
    public fun register(provider: SessionProvider<*>) {
        registered.firstOrNull { it.name == provider.name }?.let { alreadyRegistered ->
            throw IllegalArgumentException(
                "There is already registered session provider with " +
                    "name ${provider.name}: $alreadyRegistered"
            )
        }

        registered.firstOrNull { it.type == provider.type }?.let { alreadyRegistered ->
            throw IllegalArgumentException(
                "There is already registered session provider for type" +
                    " ${provider.type}: $alreadyRegistered"
            )
        }

        registered.add(provider)
    }
}
