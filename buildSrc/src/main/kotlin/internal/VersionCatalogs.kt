/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package internal

import org.gradle.accessors.dm.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.internal.catalog.*
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.accessors.runtime.*
import org.jetbrains.kotlin.gradle.plugin.*

internal val Project.libs: LibrariesForLibs
    get() = rootProject.the<LibrariesForLibs>()
