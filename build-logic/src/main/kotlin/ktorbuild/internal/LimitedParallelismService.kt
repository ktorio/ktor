/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.registerIfAbsent

private abstract class LimitedParallelismService : BuildService<BuildServiceParameters.None> {

    companion object {
        fun registerIfAbsent(project: Project, name: String, maxParallel: Int): Provider<LimitedParallelismService> {
            return project.gradle.sharedServices.registerIfAbsent(name, LimitedParallelismService::class) {
                maxParallelUsages = maxParallel
            }
        }
    }
}

/** Limits parallelism for the tasks with the same [ruleName]. */
internal fun Task.withLimitedParallelism(ruleName: String, maxParallelTasks: Int = 1) {
    val service = LimitedParallelismService.registerIfAbsent(project, ruleName, maxParallelTasks)

    usesService(service)
    doFirst { service.get() }
}
