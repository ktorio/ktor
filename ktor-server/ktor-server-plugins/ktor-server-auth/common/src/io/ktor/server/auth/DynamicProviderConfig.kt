/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

/**
 * A configuration that creates a provider based on the [AuthenticationConfig.provider] block.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DynamicProviderConfig)
 */
public class DynamicProviderConfig(
    name: String?,
    description: String? = null
) : AuthenticationProvider.Config(name, description) {

    private lateinit var authenticateFunction: suspend (context: AuthenticationContext) -> Unit

    @Deprecated("Use suspend argument", level = DeprecationLevel.HIDDEN)
    public fun authenticate(block: (context: AuthenticationContext) -> Unit) {
        authenticateFunction = { ctx -> block(ctx) }
    }

    public fun authenticate(block: suspend (context: AuthenticationContext) -> Unit) {
        authenticateFunction = block
    }

    internal fun buildProvider(): AuthenticationProvider {
        check(::authenticateFunction.isInitialized) {
            "Please configure authentication by calling authenticate() function"
        }
        return object : AuthenticationProvider(this) {
            override suspend fun onAuthenticate(context: AuthenticationContext) {
                authenticateFunction(context)
            }
        }
    }
}
