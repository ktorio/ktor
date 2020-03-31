/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.basic

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * [HttpClient] feature that sets an `Authorization: basic` header
 * as specified in RFC-2617 using [username] and [password].
 *
 * https://www.ietf.org/rfc/rfc2617.txt
 * @property username: user name.
 * @property password: user password.
 */
@Suppress("KDocMissingDocumentation", "DEPRECATION_ERROR")
@Deprecated(
    "[BasicAuth] deprecated, use [Auth] feature with [Basic] provider instead.",
    ReplaceWith("Auth"),
    level = DeprecationLevel.ERROR
)
class BasicAuth(val username: String, val password: String) {

    class Configuration {
        /**
         * Required: The username of the basic auth.
         */
        lateinit var username: String

        /**
         * Required: The password of the basic auth.
         */
        lateinit var password: String

        internal fun build(): BasicAuth = BasicAuth(username, password)
    }

    @Suppress("KDocMissingDocumentation", "DEPRECATION_ERROR")
    @Deprecated(
        "[BasicAuth] deprecated, use [Auth] feature with [Basic] provider instead.",
        ReplaceWith("Auth"),
        level = DeprecationLevel.ERROR
    )
    companion object Feature : HttpClientFeature<Configuration, BasicAuth> {

        override val key: AttributeKey<BasicAuth> = AttributeKey("AuthBasicHeader")

        override fun prepare(block: Configuration.() -> Unit): BasicAuth = Configuration().apply(block).build()

        override fun install(feature: BasicAuth, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                if (context.headers.getAll(HttpHeaders.Authorization) != null) return@intercept
                context.headers.append(HttpHeaders.Authorization, constructBasicAuthValue(feature.username, feature.password))
            }
        }

        /**
         * Create basic auth header value from [username] and [password].
         */
        fun constructBasicAuthValue(username: String, password: String): String {
            val authString = "$username:$password"
            val authBuf = authString.encodeBase64()

            return "Basic $authBuf"
        }
    }
}
