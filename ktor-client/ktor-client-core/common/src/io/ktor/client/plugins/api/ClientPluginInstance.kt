/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

public class ClientPluginInstance<PluginConfig: Any> internal constructor(internal val config: PluginConfig)
