/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*

class FakeCall(
    override val application: Application = fakeApplication(),
    override val attributes: Attributes = Attributes(),
    override val parameters: Parameters = Parameters.Empty
) : ApplicationCall {
    override val request: FakeRequest = FakeRequest(this)
    override val response: FakeResponse = FakeResponse(this)
    override fun afterFinish(handler: (Throwable?) -> Unit) {}
}
