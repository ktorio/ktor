/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.application

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationEvents", "io.ktor.server.application.*")
)
public typealias ApplicationEvents = io.ktor.events.Events

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("EventHandler", "io.ktor.server.application.*")
)
public typealias EventHandler<T> = io.ktor.events.EventHandler<T>

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("EventDefinition", "io.ktor.server.application.*")
)
public typealias EventDefinition<T> = io.ktor.events.EventDefinition<T>
