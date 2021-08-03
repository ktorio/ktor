/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.json

@Deprecated(
    message = "Moved to io.ktor.client.plugins.json",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("JsonSerializer", "io.ktor.client.plugins.json.*")
)
public interface JsonSerializer
