/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.plugins.api

/**
* Reports that [pluginName] is not installed into Ktor server but is required by some other plugin
*/
public class PluginNotInstalledException(private val pluginName: String) : Exception() {
    override val message: String?
        get() = "Plugin $pluginName is not installed but required"
}
