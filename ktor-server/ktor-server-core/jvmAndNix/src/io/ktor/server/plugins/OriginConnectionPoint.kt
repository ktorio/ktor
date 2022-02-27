/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.concurrent.*
import kotlin.native.concurrent.*
import kotlin.reflect.*

/**
 * Represents request and connection parameters possibly overridden via https headers.
 * By default it fallbacks to [ApplicationRequest.local]
 */
@Suppress("DEPRECATION")
public val ApplicationRequest.origin: RequestConnectionPoint
    get() = call.attributes.getOrNull(MutableOriginConnectionPointKey) ?: local

/**
 * A key to install a mutable [RequestConnectionPoint]
 */
@Deprecated("This API will be redesigned as per https://youtrack.jetbrains.com/issue/KTOR-2657")

public val MutableOriginConnectionPointKey: AttributeKey<MutableOriginConnectionPoint> =
    AttributeKey("MutableOriginConnectionPointKey")

/**
 * Represents a [RequestConnectionPoint]. Its every component is mutable so application plugins could modify them.
 * By default all the properties are equal to [ApplicationRequest.local] with [RequestConnectionPoint.host]
 * and [RequestConnectionPoint.port] overridden by [HttpHeaders.Host] header value.
 * Users can assign new values parsed from [HttpHeaders.Forwarded], [HttpHeaders.XForwardedHost], etc.
 * See [XForwardedHeaders] and [ForwardedHeaders].
 */
public class MutableOriginConnectionPoint internal constructor(
    delegate: RequestConnectionPoint
) : RequestConnectionPoint {

    override var version: String by AssignableWithDelegate { delegate.version }
    override var uri: String by AssignableWithDelegate { delegate.uri }
    override var method: HttpMethod by AssignableWithDelegate { delegate.method }
    override var scheme: String by AssignableWithDelegate { delegate.scheme }
    override var host: String by AssignableWithDelegate { delegate.host }
    override var port: Int by AssignableWithDelegate { delegate.port }
    override var remoteHost: String by AssignableWithDelegate { delegate.remoteHost }
}

private class AssignableWithDelegate<T : Any>(val property: () -> T) {
    private var assigned: T? = null

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any, property: KProperty<*>): T = assigned ?: property()

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        assigned = value
    }
}

internal class OriginConnectionPoint(
    private val local: RequestConnectionPoint,
    private val hostHeaderValue: String?
) : RequestConnectionPoint {
    constructor(call: ApplicationCall) : this(call.request.local, call.request.header(HttpHeaders.Host))

    override val scheme: String
        get() = local.scheme

    override val version: String
        get() = local.version

    override val port: Int
        get() = hostHeaderValue?.substringAfter(":", "80")
            ?.toIntOrNull()
            ?: local.port

    override val host: String
        get() = hostHeaderValue?.substringBefore(":")
            ?: local.host

    override val uri: String
        get() = local.uri

    override val method: HttpMethod
        get() = local.method

    override val remoteHost: String
        get() = local.remoteHost
}

/**
 * Returns [MutableOriginConnectionPoint] associated with this call
 */
@Suppress("DEPRECATION")
public val ApplicationCall.mutableOriginConnectionPoint: MutableOriginConnectionPoint
    get() = attributes.computeIfAbsent(MutableOriginConnectionPointKey) {
        MutableOriginConnectionPoint(
            OriginConnectionPoint(this)
        )
    }
