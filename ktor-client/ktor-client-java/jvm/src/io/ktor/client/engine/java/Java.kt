/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using a [Java] based backend implementation
 * with the the associated configuration [JavaHttpConfig].
 */
public object Java : HttpClientEngineFactory<JavaHttpConfig> {
    override fun create(block: JavaHttpConfig.() -> Unit): HttpClientEngine =
        JavaHttpEngine(JavaHttpConfig().apply(block))
}

@Suppress("KDocMissingDocumentation")
public class JavaHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Java

    override fun toString(): String = "Java"
}
