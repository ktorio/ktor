/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import kotlinx.cinterop.*
import platform.Foundation.*

/**
 * Challenge handler type for [NSURLSession].
 */
@OptIn(UnsafeNumber::class)
public typealias ChallengeHandler = (
    session: NSURLSession,
    task: NSURLSessionTask,
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
) -> Unit

/**
 * Custom [DarwinClientEngine] config.
 */
public class DarwinClientEngineConfig : HttpClientEngineConfig() {
    /**
     * Request configuration.
     */
    public var requestConfig: NSMutableURLRequest.() -> Unit = {}
        @Deprecated(
            "[requestConfig] property is deprecated. Consider using [configureRequest] instead",
            replaceWith = ReplaceWith("this.configureRequest(value)")
        )
        set(value) {
            field = value
        }

    /**
     * Session configuration.
     */
    public var sessionConfig: NSURLSessionConfiguration.() -> Unit = {}
        @Deprecated(
            "[sessionConfig] property is deprecated. Consider using [configureSession] instead",
            replaceWith = ReplaceWith("this.configureSession(value)")
        )
        set(value) {
            field = value
        }

    /**
     * Handles the challenge of HTTP responses [NSURLSession].
     */
    @OptIn(UnsafeNumber::class)
    public var challengeHandler: ChallengeHandler? = null
        private set

    /**
     * Appends block with [NSMutableURLRequest] configuration to [requestConfig].
     */
    public fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = requestConfig

        @Suppress("DEPRECATION")
        requestConfig = {
            old()
            block()
        }
    }

    /**
     * Appends block with [NSURLSessionConfiguration] configuration to [sessionConfig].
     */
    public fun configureSession(block: NSURLSessionConfiguration.() -> Unit) {
        val old = sessionConfig

        @Suppress("DEPRECATION")
        sessionConfig = {
            old()
            block()
        }
    }

    /**
     * Sets the [block] as an HTTP request challenge handler replacing the old one.
     */
    @OptIn(UnsafeNumber::class)
    public fun handleChallenge(block: ChallengeHandler) {
        challengeHandler = block
    }
}
