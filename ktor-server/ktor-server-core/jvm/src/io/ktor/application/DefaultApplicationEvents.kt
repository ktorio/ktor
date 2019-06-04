/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PublicApiImplicitType")
package io.ktor.application

/**
 * Event definition for Application Starting event
 *
 * Note, that application itself cannot receive this event because it fires before application is created
 * It is meant to be used by engines.
 */
val ApplicationStarting = EventDefinition<Application>()

/**
 * Event definition for Application Started event
 */
val ApplicationStarted = EventDefinition<Application>()

/**
 * Event definition for an event that is fired when the application is going to stop
 */
val ApplicationStopPreparing = EventDefinition<ApplicationEnvironment>()

/**
 * Event definition for Application Stopping event
 */
val ApplicationStopping = EventDefinition<Application>()

/**
 * Event definition for Application Stopped event
 */
val ApplicationStopped = EventDefinition<Application>()
