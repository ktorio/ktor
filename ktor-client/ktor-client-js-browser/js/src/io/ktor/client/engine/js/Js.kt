/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.util.*
import org.w3c.fetch.*

private val initHook = JsBrowser

/**
 * [HttpClientEngineFactory] using a browser fetch API to execute requests.
 */
public object JsBrowser : HttpClientEngineFactory<JsBrowser.Config> {

    public class Config : HttpClientEngineConfig() {

        /**
         * Configure the [cache behaviour](https://developer.mozilla.org/en-US/docs/Web/API/Request/cache) for the JS fetch API.
         * If null, use the default behaviour from the browser.
         */
        public var cache: RequestCache? = null

        /**
         * Configure the [credentials behaviour](https://developer.mozilla.org/en-US/docs/Web/API/Request/credentials) for the JS fetch API.
         * If null, use the default behaviour from the browser.
         */
        public var credentials: RequestCredentials? = null

        /**
         * Configure the [integrity behaviour](https://developer.mozilla.org/en-US/docs/Web/API/Request/integrity) for the JS fetch API.
         * If null, use the default behaviour from the browser.
         */
        public var integrity: String? = null

        /**
         * Configure the [mode behaviour](https://developer.mozilla.org/en-US/docs/Web/API/Request/mode) for the JS fetch API.
         * If null, use the default behaviour from the browser.
         */
        public var mode: RequestMode? = null

        internal val customFetchSettings: RequestInit.() -> Unit
            get() = {
                this@Config.cache?.let {
                    cache = it
                }
                this@Config.credentials?.let {
                    credentials = it
                }
                this@Config.integrity?.let {
                    integrity = it
                }
                this@Config.mode?.let {
                    mode = it
                }
                redirect = RequestRedirect.FOLLOW
            }
    }

    init {
        engines.add(this)
    }

    override fun create(block: Config.() -> Unit): HttpClientEngine {
        val config = Config().apply(block)
        return JsClientEngine(config, Browser(config.customFetchSettings))
    }
}
