package org.jetbrains.ktor.http

import org.jetbrains.ktor.http.response.*
import org.jetbrains.ktor.response.*


fun ApplicationResponse.link(header: LinkHeader): Unit = headers.append(HttpHeaders.Link, header.toString())
fun ApplicationResponse.link(uri: String, vararg rel: String): Unit = link(LinkHeader(uri, *rel))
