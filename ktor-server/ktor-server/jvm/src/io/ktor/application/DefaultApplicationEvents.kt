/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PublicApiImplicitType")

package io.ktor.application

import io.ktor.events.EventDefinition

/**
 * Event definition for Application Starting event
 *
 * Note, that application itself cannot receive this event because it fires before application is created
 * It is meant to be used by engines.
 */
public val ApplicationStarting: EventDefinition<Application> = EventDefinition()

/**
 * Event definition for Application Started event
 */
public val ApplicationStarted: EventDefinition<Application> = EventDefinition<Application>()

/**
 * Event definition for an event that is fired when the application is going to stop
 */
public val ApplicationStopPreparing: EventDefinition<ApplicationEnvironment> = EventDefinition<ApplicationEnvironment>()

/**
 * Event definition for Application Stopping event
 */
public val ApplicationStopping: EventDefinition<Application> = EventDefinition<Application>()

/**
 * Event definition for Application Stopped event
 */
public val ApplicationStopped: EventDefinition<Application> = EventDefinition()
