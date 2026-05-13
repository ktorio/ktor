/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * A JVM client engine that uses the Java HTTP Client introduced in Java 11.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Java)
 * ```
 * To configure the engine, pass settings exposed by [JavaHttpConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Java) {
 *     engine {
 *         // this: JavaHttpConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.java.Java)
 */
public data object Java : HttpClientEngineFactory<JavaHttpConfig> {
    init {
        allowHostHeaderInJdkHttpClient()
    }

    /**
     * Adds "host" to the JDK system property `jdk.httpclient.allowRestrictedHeaders` so that
     * `java.net.http.HttpClient` accepts a user-supplied Host header instead of rejecting it.
     *
     * The JDK reads this property once at class-load time and caches the restricted-header set,
     * so this must run before the first `java.net.http.HttpClient` usage.
     */
    private fun allowHostHeaderInJdkHttpClient() {
        val property = "jdk.httpclient.allowRestrictedHeaders"
        val existing = System.getProperty(property)
        if (existing == null) {
            System.setProperty(property, "host")
        } else if (!existing.split(",").any { it.trim().equals("host", ignoreCase = true) }) {
            System.setProperty(property, "$existing,host")
        }
    }

    override fun create(block: JavaHttpConfig.() -> Unit): HttpClientEngine =
        JavaHttpEngine(JavaHttpConfig().apply(block))
}

public class JavaHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Java

    override fun toString(): String = "Java"
}
