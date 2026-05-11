package io.ktor.compiler.services

import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.artifactsProvider
import java.io.File
import java.net.URLClassLoader

val TestServices.codeExecution: CodeExecutionService
    by TestServices.testServiceAccessor()

class CodeExecutionService(
    private val testServices: TestServices
) : TestService {

    fun generatedClassLoader(module: TestModule): GeneratedClassLoader {
        // Get the compiled artifact
        val jvmArtifact = testServices.artifactsProvider
            .getArtifact(module, ArtifactKinds.Jvm)
        val testClasspath = KtorTestEnvironmentProperties.samplesClasspath
            .map { it.toURI().toURL() }
            .toTypedArray()
        val urlClassLoader = URLClassLoader(
            testClasspath,
            this::class.java.classLoader,
        )

        // Create classloader for tests
        return GeneratedClassLoader(
            jvmArtifact.classFileFactory,
            urlClassLoader
        )
    }
}