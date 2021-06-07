// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.concurrent.*

internal actual class HttpClientCallState {
    actual var request: HttpRequest? by shared(null)
    actual var response: HttpResponse? by shared(null)
}
