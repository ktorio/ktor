/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.engine.*
import platform.Foundation.*

/**
 * Challenge handler type for [NSURLSession].
 */
typealias ChallengeHandler = (
    session: NSURLSession,
    task: NSURLSessionTask,
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
) -> Unit

/**
 * Custom [IosClientEngine] config.
 */
class IosClientEngineConfig : HttpClientEngineConfig() {
    /**
     * Request configuration.
     */
    var requestConfig: NSMutableURLRequest.() -> Unit = {}

    /**
     * Session configuration.
     */
    var sessionConfig: NSURLSessionConfiguration.() -> Unit = {}

    /**
     * Handles the challenge of HTTP responses [NSURLSession].
     */
    var challengeHandler: ChallengeHandler? = null

    /**
     * Append block with [NSMutableURLRequest] configuration to [requestConfig].
     */
    fun configureRequest(block: NSMutableURLRequest.() -> Unit) {
        val old = requestConfig

        requestConfig = {
            old()
            block()
        }
    }

    /**
     * Append block with [NSURLSessionConfiguration] configuration to [sessionConfig].
     */
    fun configureSession(block: NSURLSessionConfiguration.() -> Unit) {
        val old = sessionConfig

        sessionConfig = {
            old()
            block()
        }
    }
}
