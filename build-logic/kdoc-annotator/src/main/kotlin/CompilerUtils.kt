import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

fun forEachKtFileInDirectory(directory: Path, action: (KtFile, Path) -> Unit) {
    val project = createProjectForParsing()
    try {
        Files.walk(directory)
            .asSequence()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val ktFile = file.parseAsKtFile(project)
                action(ktFile, file)
            }
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
