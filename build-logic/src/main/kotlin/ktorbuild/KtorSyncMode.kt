/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.internal.ktorBuild
import ktorbuild.targets.KtorTargets
import org.gradle.api.Project

sealed class KtorSyncMode(internal val description: String) {
    data object Full : KtorSyncMode("All targets")

    /**
     * The mode used to reduce memory consumption on IDE sync (see KTOR-8505).
     * Allows specifying extra targets to include in the project model, e.g. `light+iosArm64`
     */
    data class Light(
        val extraTargets: List<String> = emptyList(),
    ) : KtorSyncMode("Native targets are excluded from the project model") {

        override fun toString(): String =
            if (extraTargets.isEmpty()) "Light" else "Light + $extraTargets"
    }

    internal companion object {
        fun parse(value: String): KtorSyncMode {
            val (modeName, extra) = value.split("+", limit = 2).let { it[0].lowercase() to it.getOrNull(1) }
            return when (modeName) {
                "full" -> Full
                "light" -> {
                    val extraTargets = extra.orEmpty()
                        .split(",")
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .flatMap(KtorTargets::resolveTargets)
                    val nativeTargets = KtorTargets.resolveTargets("posix")
                    val unknownTargets = extraTargets.filterNot { it in nativeTargets }
                    check(unknownTargets.isEmpty()) { "Unknown native targets: $unknownTargets" }
                    Light(extraTargets)
                }

                else -> throw IllegalArgumentException(
                    "Unknown sync mode: $value. Expected one of: full, light, light+<target>"
                )
            }
        }
    }
}

internal val KtorTargets.isLightSync: Boolean
    get() = syncMode is KtorSyncMode.Light

internal fun Project.printSyncModeNotice() {
    val syncMode = ktorBuild.targets.syncMode ?: return
    if (syncMode == KtorSyncMode.Full) return
    logger.warn("w: Sync mode: $syncMode. ${syncMode.description}")
}
