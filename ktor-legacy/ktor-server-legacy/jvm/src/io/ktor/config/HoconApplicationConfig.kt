/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.config

import com.typesafe.config.*

@Deprecated(
    message = "Moved to io.ktor.server.config",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HoconApplicationConfig", "io.ktor.server.config.*")
)
public open class HoconApplicationConfig(private val config: Config)

@Deprecated(
    message = "Moved to io.ktor.server.config",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("tryGetString(path)", "io.ktor.server.config.*")
)
public fun Config.tryGetString(path: String): String? = error("Moved to io.ktor.server.config")


@Deprecated(
    message = "Moved to io.ktor.server.config",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("tryGetStringList(path)", "io.ktor.server.config.*")
)
public fun Config.tryGetStringList(path: String): List<String>? = error("Moved to io.ktor.server.config")
