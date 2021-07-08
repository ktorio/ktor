/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.auth

@Deprecated(
    message = "Moved to io.ktor.client.plugins.auth",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("AuthProvider", "io.ktor.client.plugins.auth.*")
)
public interface AuthProvider
