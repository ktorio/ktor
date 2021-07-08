/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.application

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Application", "io.ktor.server.application.*")
)
public class Application(
    public val environment: ApplicationEnvironment
)
