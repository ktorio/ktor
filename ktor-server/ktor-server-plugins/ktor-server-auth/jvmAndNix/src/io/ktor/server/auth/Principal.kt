/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import kotlin.reflect.*

/**
 * A marker interface indicating that a class represents credentials for authentication.
 */
public interface Credential

/**
 * A marker interface indicating that a class represents an authenticated principal.
 */
public interface Principal

internal class CombinedPrincipal : Principal {
    val principals: MutableList<Pair<String?, Principal>> = mutableListOf()

    inline fun <reified T : Principal> get(provider: String?): T? {
        return get(provider, T::class)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Principal> get(provider: String?, klass: KClass<T>): T? {
        return principals
            .firstOrNull { (name, principal) ->
                if (provider != null) {
                    name == provider && klass.isInstance(principal)
                } else klass.isInstance(principal)
            }?.second as? T
    }

    fun add(provider: String?, principal: Principal) {
        principals.add(Pair(provider, principal))
    }
}
