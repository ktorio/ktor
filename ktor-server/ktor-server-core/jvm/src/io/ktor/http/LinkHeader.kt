package io.ktor.http

import io.ktor.response.*


/**
 * Append `Link` header to HTTP response
 */
fun ApplicationResponse.link(header: LinkHeader): Unit = headers.append(HttpHeaders.Link, header.toString())

/**
 * Append `Link` header to HTTP response with specified [uri] and [rel]
 */
fun ApplicationResponse.link(uri: String, vararg rel: String): Unit = link(LinkHeader(uri, *rel))
