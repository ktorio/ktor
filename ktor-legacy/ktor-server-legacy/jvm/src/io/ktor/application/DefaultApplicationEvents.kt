/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR", "PublicApiImplicitType")

package io.ktor.application

import io.ktor.events.EventDefinition

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationStarting", "io.ktor.server.application.*")
)
public val ApplicationStarting: EventDefinition<Application>
    get() = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationStarted", "io.ktor.server.application.*")
)
public val ApplicationStarted: EventDefinition<Application>
    get() = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationStopPreparing", "io.ktor.server.application.*")
)
public val ApplicationStopPreparing: EventDefinition<ApplicationEnvironment>
    get() = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationStopping", "io.ktor.server.application.*")
)
public val ApplicationStopping: EventDefinition<Application>
    get() = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationStopped", "io.ktor.server.application.*")
)
public val ApplicationStopped: EventDefinition<Application>
    get() = error("Moved to io.ktor.server.application")
