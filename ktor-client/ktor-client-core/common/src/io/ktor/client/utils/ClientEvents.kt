/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import kotlin.native.concurrent.*

/**
 * Happens after creating of the new request.
 */
@SharedImmutable
public val HttpRequestCreated: EventDefinition<HttpRequestBuilder> = EventDefinition()

/**
 * Happens after execution of all interceptor before sending the request.
 */
@SharedImmutable
public val HttpRequestIsReadyForSending: EventDefinition<HttpRequestBuilder> = EventDefinition()

/**
 * Happens after response headers received.
 */
@SharedImmutable
public val HttpResponseReceived: EventDefinition<HttpResponse> = EventDefinition()

/**
 * Utility class containing response and fail reason for [HttpResponseReceiveFailed] event.
 */
public class HttpResponseReceiveFail(public val response: HttpResponse, public val cause: Throwable)

/**
 * Event happens when the body receiving is failed with exception.
 */
public val HttpResponseReceiveFailed: EventDefinition<HttpResponseReceiveFail> = EventDefinition()

/**
 * Happens when response got cancelled with exception.
 */
@SharedImmutable
public val HttpResponseCancelled: EventDefinition<HttpResponse> = EventDefinition()
