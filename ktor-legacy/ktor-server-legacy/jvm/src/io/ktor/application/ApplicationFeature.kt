/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR", "UNUSED_PARAMETER", "KDocMissingDocumentation")

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
public interface ApplicationFeature<
    in TPipeline : Pipeline<*, ApplicationCall>,
    out TConfiguration : Any,
    TFeature : Any>

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("plugin(plugin)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.feature(feature: ApplicationFeature<A, B, F>): F =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("pluginOrNull(plugin)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.featureOrNull(
    feature: ApplicationFeature<A, B, F>
): F? = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("install(plugin, configure)", "io.ktor.server.application.*")
)
public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(
    feature: ApplicationFeature<P, B, F>,
    configure: B.() -> Unit = {}
): F = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("uninstallAllPlugins()", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>> A.uninstallAllFeatures(): Unit =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("uninstall(plugin)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.uninstall(
    feature: ApplicationFeature<A, B, F>
): Unit = error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("uninstallPlugin(key)", "io.ktor.server.application.*")
)
public fun <A : Pipeline<*, ApplicationCall>, F : Any> A.uninstallFeature(key: AttributeKey<F>): Unit =
    error("Moved to io.ktor.server.application")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DuplicateApplicationPluginException", "io.ktor.server.application.*")
)
public class DuplicateApplicationFeatureException(message: String) : Exception(message)

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("MissingApplicationPluginException", "io.ktor.server.application.*")
)
public class MissingApplicationFeaatureException(
    public val key: AttributeKey<*>
)
