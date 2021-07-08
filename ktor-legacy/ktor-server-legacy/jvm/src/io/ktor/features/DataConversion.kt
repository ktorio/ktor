/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DataConversion", "io.ktor.server.plugins.*")
)
public object DataConversion
