/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import ktorbuild.internal.resolveVersion

version = resolveVersion()

ProjectTagsService.register(project)
extensions.create<KtorBuildExtension>(KtorBuildExtension.NAME)

registerPackageJsonAggregationTask()
