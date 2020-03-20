/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers.oauth

interface TokenProvider {

    suspend fun getToken(): String?

    suspend fun invalidateToken(token: String)
}
