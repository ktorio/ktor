/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PublicApiImplicitType")

package io.ktor.server.application

import io.ktor.events.EventDefinition
import kotlin.native.concurrent.*

/**
 * Event definition for Application Starting event
 *
 * Note, that application itself cannot receive this event because it fires before application is created
 * It is meant to be used by engines.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationStarting)
 */
public val ApplicationStarting: EventDefinition<Application> = EventDefinition()

/**
 * Event definition for Application Started event
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationStarted)
 */
public val ApplicationStarted: EventDefinition<Application> = EventDefinition()

/**
 * Fired when the server is ready to accept connections
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerReady)
 */
public val ServerReady: EventDefinition<ApplicationEnvironment> = EventDefinition()

/**
 * Event definition for an event that is fired when the application is going to stop
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationStopPreparing)
 */
public val ApplicationStopPreparing: EventDefinition<ApplicationEnvironment> = EventDefinition()

/**
 * Event definition for Application Stopping event
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationStopping)
 */
public val ApplicationStopping: EventDefinition<Application> = EventDefinition()

/**
 * Event definition for Application Stopped event
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationStopped)
 */
public val ApplicationStopped: EventDefinition<Application> = EventDefinition()
