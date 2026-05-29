/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// We want these extensions to be available without import
@file:Suppress("PackageDirectoryMismatch")

import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.provider.Provider
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector

@Suppress("UnusedReceiverParameter") // Restrict scope
fun DependencySubstitutions.module(provider: Provider<MinimalExternalModuleDependency>): ComponentSelector {
    val dependency = provider.get()
    val id = dependency.module
    val version = checkNotNull(dependency.version) { "Must specify version for target of dependency substitution" }
    return DefaultModuleComponentSelector.newSelector(id, version)
}
