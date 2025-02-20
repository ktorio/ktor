/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.KtorBuildExtension
import ktorbuild.internal.resolveVersion

version = resolveVersion()

extensions.create<KtorBuildExtension>(KtorBuildExtension.NAME)
