import org.tomlj.*
import org.gradle.kotlin.dsl.*
import java.nio.file.*

/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

private val versions by lazy {
    val tomlFile = Paths.get("gradle/libs.versions.toml")
    val toml = Toml.parse(tomlFile)
    object {
        operator fun get(key: String): String =
            toml.getString("versions.$key")
                ?: throw IllegalArgumentException("Version for $key not found in versions catalog $tomlFile")
    }
}

object Versions {
    val kotlin = versions["kotlin-version"]
    val coroutines = versions["coroutines-version"]
    val slf4j = versions["slf4j-version"]
    val junit = versions["junit-version"]
    val logback = versions["logback-version"]
    val puppeteer = versions["puppeteer-version"]
}
