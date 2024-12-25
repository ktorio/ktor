/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.targets.KtorTargets
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

abstract class KtorBuildExtension(
    objects: ObjectFactory,
    val targets: KtorTargets
) {

    @Inject
    constructor(objects: ObjectFactory) : this(objects, targets = objects.newInstance())

    companion object {
        const val NAME = "ktorBuild"
    }
}
