/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.network

/**
 * Represents remote endpoint with [hostname] and [port].
 *
 * The address will be resolved after construction.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.network.NetworkAddress)
 *
 * @throws UnresolvedAddressException if the [hostname] cannot be resolved.
 */
public expect abstract class NetworkAddress

/**
 * Represents remote endpoint with [hostname] and [port].
 *
 * The address will be resolved after construction.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.network.NetworkAddress)
 *
 * @throws UnresolvedAddressException if the [hostname] cannot be resolved.
 */
public expect fun NetworkAddress(hostname: String, port: Int): NetworkAddress

/**
 * Network address hostname. This may trigger a reverse lookup.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.network.hostname)
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public expect val NetworkAddress.hostname: String

/**
 * Network address. Usually, it's an IP address in string form. This will not trigger a reverse lookup.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.network.address)
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public expect val NetworkAddress.address: String

/**
 * Network address port.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.network.port)
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public expect val NetworkAddress.port: Int

public expect class UnresolvedAddressException() : IllegalArgumentException
