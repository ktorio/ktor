/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.http.Uri.*

/**
 * A mutable façade over the generic Uri interface. Only assigned properties are returned in the results.
 */
public class UriBuilder(
    private val parent: UriReference
): MutableUriReference {

    override var protocol: UrlProtocol? = null
        get() = field ?: parent.protocol

    override var protocolSeparator: ProtocolSeparator? = null
        get() = field ?: parent.protocolSeparator

    override var authority: Authority? = null
        get() = field ?: parent.authority
        set(value) { field = value; encodedHost = value?.host?.encodeURLPathPart() }

    override var host: String?
        get() = authority?.host ?: parent.host
        set(value) {
            authority = Authority(authority?.userInfo, value, authority?.port)
            encodedHost = value?.encodeURLPathPart()
        }

    override var port: Int?
        get() = authority?.port ?: parent.port
        set(value) { authority = Authority(authority?.userInfo, authority?.host, value) }

    override var user: String?
        get() = authority?.user ?: parent.user
        set(value) {
            authority = Authority(value?.let { UserInfo(it, authority?.password) }, authority?.host, authority?.port)
            encodedUser = value?.encodeURLPathPart()
        }

    override var password: String?
        get() = authority?.password ?: parent.password
        set(value) {
            authority = Authority(value?.let { authority?.userInfo?.copy(credential = value) }, authority?.host, authority?.port)
            encodedPassword = value?.encodeURLPathPart()
        }

    override var path: Path? = null
        get() = field ?: parent.path
        set(value) {
            field = value
            encodedPath = value?.toString()
        }

    override var parameters: ParametersBuilder =
        QueryParametersBuilder(parent.parameters)

    override var fragment: String? = null
        get() = field ?: parent.fragment
        set(value) {
            field = value
            encodedFragment = value?.encodeURLPathPart()
        }

    override var encodedHost: String? = null
        get() = field ?: parent.encodedHost
        private set

    override var encodedUser: String? = null
        get() = field ?: parent.encodedUser
        private set

    override var encodedPassword: String? = null
        get() = field ?: parent.encodedPassword
        private set

    override var encodedPath: String? = null
        get() = field ?: parent.encodedPath
        private set

    override var encodedQuery: String? = null
        get() = field ?: parent.encodedQuery
        private set

    override var encodedFragment: String? = null
        get() = field ?: parent.encodedFragment
        private set

    override fun toString(): String =
        formatToString()
}

internal class QueryParametersBuilder(
    val parent: Parameters?,
    val delegate: ParametersBuilder = ParametersBuilder(),
): ParametersBuilder by delegate {

}
