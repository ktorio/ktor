/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.kotlin.dsl.support.serviceOf

val features = serviceOf<BuildFeatures>()

if (features.configurationCache.requested.get()) {
    // KT-72933: Storing these fields leads to OOM
    ConfigurationCacheWorkarounds.addIgnoredBeanFields(
        "transformationParameters" to "org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyTransformationTask",
        "parameters" to "org.jetbrains.kotlin.gradle.targets.native.internal.CInteropMetadataDependencyTransformationTask",
    )

    // Mark these tasks as incompatible with configuration cache as we partially exclude their state from CC
    gradle.beforeProject {
        tasks.matching { it::class.java.simpleName.contains("MetadataDependencyTransformationTask") }
            .configureEach { notCompatibleWithConfigurationCache("Workaround for KT-72933") }
    }

    println("Configuration Cache: Workaround for KT-72933 was applied")
}
