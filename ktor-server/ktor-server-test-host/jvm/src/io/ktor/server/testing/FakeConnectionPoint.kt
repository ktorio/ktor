/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.HttpMethod
import org.eclipse.jetty.http.*

class FakeConnectionPoint(
    override val scheme: String = HttpProtocolVersion.HTTP_1_1.toString(),
    override val version: String = HttpVersion.HTTP_1_1.toString(),
    override val port: Int = DEFAULT_PORT,
    override val host: String = "localhost",
    override val uri: String = "/",
    override val method: HttpMethod = HttpMethod.Get,
    override val remoteHost: String = "localhost"
) : RequestConnectionPoint
