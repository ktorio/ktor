/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HostRouteSelector", "io.ktor.server.routing.*")
)
public data class HostRouteSelector(
    val hostList: List<String>,
    val hostPatterns: List<Regex>,
    val portsList: List<Int>
) : RouteSelector()
