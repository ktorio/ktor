/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.application

import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationPlugin", "io.ktor.server.application.*")
)
@Suppress("AddVarianceModifier")
public interface ApplicationPlugin<
    in TPipeline : Pipeline<*, ApplicationCall>,
    out TConfiguration : Any,
    TPlugin : Any>

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("plugin(plugin)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.plugin(plugin: ApplicationPlugin<A, B, F>): F =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("pluginOrNull(plugin)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.pluginOrNull(plugin: ApplicationPlugin<A, B, F>): F? =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("install(plugin, configure)", "io.ktor.server.application.*")
)
public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(
    plugin: ApplicationPlugin<P, B, F>,
    configure: B.() -> Unit = {}
): F = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("uninstallAllPlugins()", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>> A.uninstallAllPlugins(): Unit =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("uninstall(plugin)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.uninstall(
    plugin: ApplicationPlugin<A, B, F>
): Unit = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("uninstallPlugin(key)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, F : Any> A.uninstallPlugin(key: AttributeKey<F>): Unit =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DuplicateApplicationPluginException", "io.ktor.server.application.*")
)
public class DuplicateApplicationPluginException(message: String) : Exception(message)

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("MissingApplicationPluginException", "io.ktor.server.application.*")
)
public class MissingApplicationPluginException(
    public val key: AttributeKey<*>
)
