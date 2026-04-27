/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.gradle

import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider

@Suppress("UNCHECKED_CAST")
@JvmName("maybeNamedTyped")
internal fun <T : Task> TaskCollection<Task>.maybeNamed(name: String): TaskProvider<T>? =
    maybeNamed(name) as? TaskProvider<T>

internal fun TaskCollection<Task>.maybeNamed(name: String): TaskProvider<Task>? {
    return if (name in names) named(name) else null
}

internal fun TaskCollection<Task>.maybeNamed(name: String, configure: Task.() -> Unit) {
    if (name in names) named(name).configure(configure)
}
