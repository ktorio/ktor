/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

/**
 * Creates an [ApplicationEngineEnvironment] instance from command line arguments
 */
public fun commandLineEnvironment(
    args: Array<String>,
    environmentBuilder: ApplicationEngineEnvironmentBuilder.() -> Unit = {}
): ApplicationEngineEnvironment {
    return buildCommandLineEnvironment(args, environmentBuilder)
}
