/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.internal.*
import io.ktor.utils.io.KtorDsl
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Foundation.NSCharacterSet

/**
 * A challenge handler type for [NSURLSession].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.ChallengeHandler)
 */
@OptIn(UnsafeNumber::class)
public typealias ChallengeHandler = (
    session: NSURLSession,
    task: NSURLSessionTask,
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
) -> Unit

/**
 * Configuration of allowed character sets used for URL percent-encoding.
 *
 * Each property defines the set of characters that can appear unescaped
 * in the corresponding URL component. Characters not included in the
 * configured set will be percent-encoded during URL construction.
 *
 * By default, the configuration uses `NSCharacterSet.URL*AllowedCharacterSet` values.
 */
@KtorDsl
public class UrlAllowedCharactersConfig {

    /**
     * Allowed characters for the user information component of a URL
     * (for example, the username in `https://user@example.com`).
     *
     * Defaults to [NSCharacterSet.URLUserAllowedCharacterSet].
     */
    public var userAllowedCharacterSet: NSCharacterSet = NSCharacterSet.URLUserAllowedCharacterSet

    /**
     * Allowed characters for the host component of a URL
     * (for example, `example.com`).
     *
     * Defaults to [NSCharacterSet.URLHostAllowedCharacterSet].
     */
    public var hostAllowedCharacterSet: NSCharacterSet = NSCharacterSet.URLHostAllowedCharacterSet

    /**
     * Allowed characters for the path component of a URL
     * (for example, `/api/v1/users`).
     *
     * Defaults to [NSCharacterSet.URLPathAllowedCharacterSet].
     */
    public var pathAllowedCharacterSet: NSCharacterSet = NSCharacterSet.URLPathAllowedCharacterSet

    /**
     * Allowed characters for the query component of a URL
     * (for example, `page=1&sort=name`).
     *
     * Defaults to [NSCharacterSet.URLQueryAllowedCharacterSet].
     */
    public var queryAllowedCharacterSet: NSCharacterSet = NSCharacterSet.URLQueryAllowedCharacterSet

    /**
     * Allowed characters for the fragment component of a URL
     * (for example, `section-1` in `https://example.com#section-1`).
     *
     * Defaults to [NSCharacterSet.URLFragmentAllowedCharacterSet].
     */
    public var fragmentAllowedCharacterSet: NSCharacterSet = NSCharacterSet.URLFragmentAllowedCharacterSet
}

/**
 * A configuration for the [Darwin] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig)
 */
public class DarwinClientEngineConfig : HttpClientEngineConfig() {
    /**
     * A request configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.requestConfig)
     */
    public var requestConfig: NSMutableURLRequest.() -> Unit = {}
        @Deprecated(
            "[requestConfig] property is deprecated. Consider using [configureRequest] instead",
            replaceWith = ReplaceWith("this.configureRequest(value)"),
            level = DeprecationLevel.ERROR
        )
        set

    /**
     * A session configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.sessionConfig)
     */
    public var sessionConfig: NSURLSessionConfiguration.() -> Unit = {}
        @Deprecated(
            "[sessionConfig] property is deprecated. Consider using [configureSession] instead",
            replaceWith = ReplaceWith("this.configureSession(value)"),
            level = DeprecationLevel.ERROR
        )
        set

    /**
     * Handles the challenge of HTTP responses [NSURLSession].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.challengeHandler)
     */
    @OptIn(UnsafeNumber::class)
    public var challengeHandler: ChallengeHandler? = null
        private set

    /**
     * Specifies a session to use for making HTTP requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.preconfiguredSession)
     */
    public var preconfiguredSession: NSURLSession? = null
        private set

    /**
     * Specifies allowed character sets used for URL percent-encoding.
     */
    internal val urlAllowedCharactersConfig: UrlAllowedCharactersConfig = UrlAllowedCharactersConfig()

    /**
     * Specifies a session to use for making HTTP requests.
     */
    internal var sessionAndDelegate: Pair<NSURLSession, KtorNSURLSessionDelegate>? = null

    /**
     * Appends a block with the [NSMutableURLRequest] configuration to [requestConfig].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.configureRequest)
     */
    public fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = requestConfig

        @Suppress("DEPRECATION_ERROR")
        requestConfig = {
            old()
            block()
        }
    }

    /**
     * Appends a block with the [NSURLSessionConfiguration] configuration to [sessionConfig].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.configureSession)
     */
    public fun configureSession(block: NSURLSessionConfiguration.() -> Unit) {
        val old = sessionConfig

        @Suppress("DEPRECATION_ERROR")
        sessionConfig = {
            old()
            block()
        }
    }

    /**
     * Set a [session] to be used to make HTTP requests, [null] to create default session.
     * If the preconfigured session is set, [configureSession] block will be ignored.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.usePreconfiguredSession)
     */
    @Deprecated("Please use method with delegate parameter", level = DeprecationLevel.ERROR)
    public fun usePreconfiguredSession(session: NSURLSession?) {
        preconfiguredSession = session
    }

    /**
     * Set a [session] to be used to make HTTP requests.
     * If the preconfigured session is set, [configureSession] and [handleChallenge] blocks will be ignored.
     *
     * The [session] must be created with [KtorNSURLSessionDelegate] as a delegate.
     *
     * ```
     * val delegate = KtorNSURLSessionDelegate()
     * val session = NSURLSession.sessionWithConfiguration(
     *     NSURLSessionConfiguration.defaultSessionConfiguration(),
     *     delegate,
     *     delegateQueue = null
     * )
     *
     * usePreconfiguredSession(session, delegate)
     * ```
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.usePreconfiguredSession)
     *
     * @see [KtorNSURLSessionDelegate] for details.
     */
    public fun usePreconfiguredSession(session: NSURLSession, delegate: KtorNSURLSessionDelegate) {
        requireNotNull(session.delegate) {
            """
                Invalid session: delegate field is null
                Possible solutions:

                1. Ensure that you set a valid delegate when creating the `session`. For more details, see `KtorNSURLSessionDelegate`.

                2. If you're only modifying session configuration, consider using `configureSession` instead of `usePreconfiguredSession`.
            """.trimIndent()
        }
        sessionAndDelegate = session to delegate
    }

    /**
     * Sets the [block] as an HTTP request challenge handler replacing the old one.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.darwin.DarwinClientEngineConfig.handleChallenge)
     */
    @OptIn(UnsafeNumber::class)
    public fun handleChallenge(block: ChallengeHandler) {
        challengeHandler = block
    }

    /**
     * Configures the set of allowed URL characters using the provided [block].
     */
    public fun configureUrlAllowedCharacters(block: UrlAllowedCharactersConfig.() -> Unit) {
        urlAllowedCharactersConfig.block()
    }
}
