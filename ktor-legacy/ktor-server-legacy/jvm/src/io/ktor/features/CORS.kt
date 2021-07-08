/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "DEPRECATION_ERROR")

package io.ktor.features

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CORS", "io.ktor.server.plugins.*")
)
public class CORS
