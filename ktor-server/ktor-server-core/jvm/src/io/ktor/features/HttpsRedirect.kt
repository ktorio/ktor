/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Redirect non-secure requests to HTTPS
 */
public class HttpsRedirect(config: Configuration) {
    /**
     * HTTPS port to redirect to
     */
    public val redirectPort: Int = config.sslPort

    /**
     * If it does permanent redirect
     */
    public val permanent: Boolean = config.permanentRedirect

    /**
     * The list of call predicates for redirect exclusion.
     * Any call matching any of the predicates will not be redirected by this feature.
     */
    public val excludePredicates: List<(ApplicationCall) -> Boolean> = config.excludePredicates.toList()

    /**
     * Redirect feature configuration
     */
    public class Configuration {
        /**
         * HTTPS port (443 by default) to redirect to
         */
        public var sslPort: Int = URLProtocol.HTTPS.defaultPort

        /**
         * Use permanent redirect or temporary
         */
        public var permanentRedirect: Boolean = true

        /**
         * The list of call predicates for redirect exclusion.
         * Any call matching any of the predicates will not be redirected by this feature.
         */
        public val excludePredicates: MutableList<(ApplicationCall) -> Boolean> = ArrayList()

        /**
         * Exclude calls with paths matching the [pathPrefix] from being redirected to https by this feature.
         */
        public fun excludePrefix(pathPrefix: String) {
            exclude { call ->
                call.request.origin.uri.startsWith(pathPrefix)
            }
        }

        /**
         * Exclude calls with paths matching the [pathSuffix] from being redirected to https by this feature.
         */
        public fun excludeSuffix(pathSuffix: String) {
            exclude { call ->
                call.request.origin.uri.endsWith(pathSuffix)
            }
        }

        /**
         * Exclude calls matching the [predicate] from being redirected to https by this feature.
         */
        public fun exclude(predicate: (call: ApplicationCall) -> Boolean) {
            excludePredicates.add(predicate)
        }
    }

    /**
     * Feature installation object
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HttpsRedirect> {
        override val key: AttributeKey<HttpsRedirect> = AttributeKey("HttpsRedirect")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HttpsRedirect {
            val feature = HttpsRedirect(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Features) {
                if (call.request.origin.scheme == "http" &&
                    feature.excludePredicates.none { predicate -> predicate(call) }
                ) {
                    val redirectUrl = call.url { protocol = URLProtocol.HTTPS; port = feature.redirectPort }
                    call.respondRedirect(redirectUrl, feature.permanent)
                    finish()
                }
            }
            return feature
        }
    }
}
