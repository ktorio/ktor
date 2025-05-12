/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*

plugins {
    id("ktorbuild.kmp")
    id("ktorbuild.dokka")
    id("ktorbuild.publish")
    id("ktorbuild.compatibility")
}

addProjectTag(ProjectTag.Library)
