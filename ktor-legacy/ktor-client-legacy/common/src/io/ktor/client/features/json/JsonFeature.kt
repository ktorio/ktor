/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR", "UNUSED_PARAMETER", "KDocMissingDocumentation")

package io.ktor.client.features.json

@Deprecated(
    message = "Moved to io.ktor.client.plugins.json",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("JsonPlugin", "io.ktor.client.plugins.json.*")
)
public class JsonFeature

@Deprecated(
    message = "Moved to io.ktor.client.plugins.json",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Json(block)", "io.ktor.client.plugins.json.*")
)
public fun Json(block: Any): Unit = error("Moved to io.ktor.client.plugins.json")
