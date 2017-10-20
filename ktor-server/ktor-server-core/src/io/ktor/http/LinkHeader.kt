package io.ktor.http

import io.ktor.response.*


fun ApplicationResponse.link(header: LinkHeader): Unit = headers.append(HttpHeaders.Link, header.toString())
fun ApplicationResponse.link(uri: String, vararg rel: String): Unit = link(LinkHeader(uri, *rel))
