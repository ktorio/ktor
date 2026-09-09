/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.kdoc

import java.nio.file.Path
import kotlin.io.path.Path as createPath

private const val EXCLUDE_ARGUMENT_PREFIX = "--exclude="

fun main(args: Array<String>) {
    val excludeArguments = args.drop(2)
    if (args.size < 2 || excludeArguments.any { !it.startsWith(EXCLUDE_ARGUMENT_PREFIX) }) {
        printHelp()
        return
    }

    val projectSources = args[0]
    val link = args[1]
    val excludedPathPatterns = excludeArguments.map { it.removePrefix(EXCLUDE_ARGUMENT_PREFIX) }

    updateKDocsInDirectory(createPath(projectSources), link, excludedPathPatterns)
}

private fun updateKDocsInDirectory(directory: Path, link: String, excludedPathPatterns: List<String>) {
    val pathExclusions = PathExclusions(excludedPathPatterns)
    forEachKtFileInDirectory(
        directory,
        shouldVisit = { it !in pathExclusions },
    ) { ktFile, path ->
        if (path.isInTestSourceSet()) {
            removeFeedbackLinksFromKDocs(ktFile, path)
        } else {
            annotatePublicApiKDocs(ktFile, path, link)
        }
    }
}

internal fun Path.isInTestSourceSet(): Boolean {
    val parent = parent ?: return false
    return parent.iterator().asSequence()
        .map { it.toString() }
        .any { sourceSet ->
            sourceSet == "test" ||
                sourceSet.endsWith("Test") ||
                sourceSet.startsWith("test") && sourceSet.getOrNull("test".length)?.isUpperCase() == true
        }
}

private fun printHelp() {
    println("KDoc annotator is a tool to add a feedback link for the KDoc documentation if this link is not present yet.")
    println("Usage:")
    println("kdoc-annotator <path-to-project sources> <link> [${EXCLUDE_ARGUMENT_PREFIX}<path-pattern>]...")
}
