/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*

class FakeRequest(
    override val call: ApplicationCall,
    override val pipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(true),
    override var queryParameters: Parameters = Parameters.Empty,
    override var headers: Headers = Headers.Empty,
    override var local: RequestConnectionPoint = FakeConnectionPoint(),
    val body: ByteReadChannel = ByteReadChannel.Empty
) : ApplicationRequest {
    override val cookies: RequestCookies = RequestCookies(this)

    override fun receiveChannel(): ByteReadChannel {
        return body
    }
}
