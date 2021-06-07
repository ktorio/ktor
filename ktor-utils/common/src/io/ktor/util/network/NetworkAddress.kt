/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.network

/**
 * Represents remote endpoint with [hostname] and [port].
 *
 * The address will be resolved after construction.
 *
 * @throws UnresolvedAddressException if the [hostname] cannot be resolved.
 */
public expect class NetworkAddress

/**
 * Represents remote endpoint with [hostname] and [port].
 *
 * The address will be resolved after construction.
 *
 * @throws UnresolvedAddressException if the [hostname] cannot be resolved.
 */
public expect fun NetworkAddress(hostname: String, port: Int): NetworkAddress

/**
 * Network address hostname.
 */
public expect val NetworkAddress.hostname: String

/**
 * Network address port.
 */
public expect val NetworkAddress.port: Int

@Suppress("KDocMissingDocumentation")
public expect class UnresolvedAddressException() : IllegalArgumentException
