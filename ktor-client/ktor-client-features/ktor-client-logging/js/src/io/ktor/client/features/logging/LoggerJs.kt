/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

@Suppress("DEPRECATION", "KDocMissingDocumentation")
@Deprecated(
    "Use ktor utils Logger.Default instead.",
    ReplaceWith("Logger.Default", "io.ktor.util.logging.Logger.Default")
)
actual val Logger.Companion.DEFAULT: Logger get() = SIMPLE
