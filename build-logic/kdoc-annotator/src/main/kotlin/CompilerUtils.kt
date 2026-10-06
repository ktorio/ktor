/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.kdoc

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

fun forEachKtFileInDirectory(
    directory: Path,
    shouldVisit: (Path) -> Boolean = { true },
    action: (KtFile, Path) -> Unit,
) {
    val project = createProjectForParsing()
    try {
        Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(directoryToVisit: Path, attributes: BasicFileAttributes): FileVisitResult {
                val relativePath = directory.relativize(directoryToVisit)
                return if (directoryToVisit == directory || shouldVisit(relativePath)) {
                    FileVisitResult.CONTINUE
                } else {
                    FileVisitResult.SKIP_SUBTREE
                }
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                val relativePath = directory.relativize(file)
                if (file.extension == "kt" && shouldVisit(relativePath)) {
                    val ktFile = file.parseAsKtFile(project)
                    action(ktFile, file)
                }
                return FileVisitResult.CONTINUE
            }
        })
    } finally {
        Disposer.dispose(project)
    }
}

private fun Path.parseAsKtFile(project: Project): KtFile {
    return PsiFileFactoryImpl(project).createFileFromText(name, KotlinLanguage.INSTANCE, readText()) as KtFile
}

@OptIn(K1Deprecation::class)
private fun createProjectForParsing(): Project {
    return KotlinCoreEnvironment.createForProduction(
        Disposer.newDisposable("KDoc Annotator"),
        CompilerConfiguration(),
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    ).project
}
